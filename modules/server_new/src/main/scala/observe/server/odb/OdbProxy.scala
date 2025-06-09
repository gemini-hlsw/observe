// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.Applicative
import cats.MonadThrow
import cats.effect.Resource
import cats.effect.Sync
import cats.effect.kernel.Ref
import cats.syntax.all.*
import clue.FetchClient
import clue.data.syntax.*
import clue.syntax.*
import eu.timepit.refined.types.numeric.NonNegShort
import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.enums.AtomStage
import lucuma.core.enums.DatasetStage
import lucuma.core.enums.Instrument
import lucuma.core.enums.ObserveClass
import lucuma.core.enums.SequenceCommand
import lucuma.core.enums.SequenceType
import lucuma.core.enums.StepStage
import lucuma.core.model.Observation
import lucuma.core.model.Visit
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Dataset
import lucuma.core.model.sequence.Step
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.TelescopeConfig
import lucuma.core.model.sequence.TelescopeConfig as CoreTelescopeConfig
import lucuma.core.model.sequence.flamingos2.Flamingos2DynamicConfig
import lucuma.core.model.sequence.flamingos2.Flamingos2StaticConfig
import lucuma.core.model.sequence.gmos
import lucuma.schemas.ObservationDB
import lucuma.schemas.ObservationDB.Scalars.VisitId
import lucuma.schemas.ObservationDB.Types.RecordAtomInput
import lucuma.schemas.ObservationDB.Types.RecordFlamingos2StepInput
import lucuma.schemas.ObservationDB.Types.RecordGmosNorthStepInput
import lucuma.schemas.ObservationDB.Types.RecordGmosSouthStepInput
import lucuma.schemas.odb.input.*
import observe.common.ObsQueriesGQL.*
import observe.model.dhs.*
import observe.model.odb.ObsRecordedIds
import observe.model.odb.RecordedAtomId
import observe.model.odb.RecordedStepId
import observe.server.ObserveFailure
import org.typelevel.log4cats.Logger

sealed trait OdbEventCommands[F[_]] {
  def visitStart[S](
    obsId:     Observation.Id,
    staticCfg: S
  ): F[Unit]
  def sequenceStart(
    obsId: Observation.Id
  ): F[Unit]
  def atomStart(
    obsId:        Observation.Id,
    instrument:   Instrument,
    sequenceType: SequenceType,
    stepCount:    NonNegShort,
    generatedId:  Option[Atom.Id]
  ): F[Unit]
  def stepStartStep[D](
    obsId:           Observation.Id,
    dynamicConfig:   D,
    stepConfig:      StepConfig,
    telescopeConfig: CoreTelescopeConfig,
    observeClass:    ObserveClass,
    generatedId:     Option[Step.Id]
  ): F[Unit]
  def stepStartConfigure(obsId:  Observation.Id): F[Unit]
  def stepEndConfigure(obsId:    Observation.Id): F[Boolean]
  def stepStartObserve(obsId:    Observation.Id): F[Boolean]
  def datasetStartExposure(
    obsId:  Observation.Id,
    fileId: ImageFileId
  ): F[RecordDatasetMutation.Data.RecordDataset.Dataset]
  def datasetEndExposure(obsId:  Observation.Id, fileId: ImageFileId): F[Boolean]
  def datasetStartReadout(obsId: Observation.Id, fileId: ImageFileId): F[Boolean]
  def datasetEndReadout(obsId:   Observation.Id, fileId: ImageFileId): F[Boolean]
  def datasetStartWrite(obsId:   Observation.Id, fileId: ImageFileId): F[Boolean]
  def datasetEndWrite(obsId:     Observation.Id, fileId: ImageFileId): F[Boolean]
  def stepEndObserve(obsId:      Observation.Id): F[Boolean]
  def stepEndStep(obsId:         Observation.Id): F[Boolean]
  def stepAbort(obsId:           Observation.Id): F[Boolean]
  def stepStop(obsId:            Observation.Id): F[Boolean]
  def atomEnd(obsId:             Observation.Id): F[Boolean]
  def obsContinue(obsId:         Observation.Id): F[Boolean]
  def obsPause(obsId:            Observation.Id, reason: String): F[Boolean]
  def obsStop(obsId:             Observation.Id, reason: String): F[Boolean]

  def getCurrentRecordedIds: F[ObsRecordedIds]
}

trait OdbProxy[F[_]] extends OdbEventCommands[F] {
  def read(oid:               Observation.Id): F[ObsQuery.Data.Observation]
  def resetAcquisition(obsId: Observation.Id): F[Unit]

  def obsEditSubscription(obsId: Observation.Id): Resource[F, fs2.Stream[F, Unit]]
}

object OdbProxy {
  def apply[F[_]](
    evCmds:     OdbEventCommands[F],
    subscriber: OdbSubscriber[F]
  )(using Sync[F], FetchClient[F, ObservationDB]): OdbProxy[F] =
    new OdbProxy[F] {
      def read(oid: Observation.Id): F[ObsQuery.Data.Observation] =
        ObsQuery[F]
          .query(oid)
          .raiseGraphQLErrors
          .flatMap:
            _.observation.fold(
              Sync[F].raiseError[ObsQuery.Data.Observation]:
                ObserveFailure.Unexpected(s"OdbProxy: Unable to read observation $oid")
            )(_.pure[F])

      def resetAcquisition(obsId: Observation.Id): F[Unit] =
        ResetAcquisitionMutation[F].execute(obsId = obsId).void

      export evCmds.*
      export subscriber.obsEditSubscription
    }

  class DummyOdbCommands[F[_]: Sync] extends OdbEventCommands[F] {
    override def sequenceStart(
      obsId: Observation.Id
    ): F[Unit] =
      ().pure[F]

    override def stepStartStep[D](
      obsId:           Observation.Id,
      dynamicConfig:   D,
      stepConfig:      StepConfig,
      telescopeConfig: CoreTelescopeConfig,
      observeClass:    ObserveClass,
      generatedId:     Option[Step.Id]
    ): F[Unit] = ().pure[F]

    override def stepStartConfigure(obsId: Observation.Id): F[Unit] = Applicative[F].unit

    override def stepEndConfigure(obsId: Observation.Id): F[Boolean] =
      false.pure[F]

    override def stepStartObserve(obsId: Observation.Id): F[Boolean] =
      false.pure[F]

    override def datasetStartExposure(
      obsId:  Observation.Id,
      fileId: ImageFileId
    ): F[RecordDatasetMutation.Data.RecordDataset.Dataset] =
      Sync[F]
        .delay(scala.util.Random.between(1L, Long.MaxValue))
        .map(x =>
          RecordDatasetMutation.Data.RecordDataset.Dataset(Dataset.Id(PosLong.unsafeFrom(x)), None)
        )

    override def datasetEndExposure(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      true.pure[F]

    override def stepEndObserve(obsId: Observation.Id): F[Boolean] =
      false.pure[F]

    override def stepEndStep(obsId: Observation.Id): F[Boolean] = false.pure[F]

    def stepAbort(obsId: Observation.Id): F[Boolean] = false.pure[F]

    def stepStop(obsId: Observation.Id): F[Boolean] = false.pure[F]

    def atomEnd(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def obsContinue(obsId: Observation.Id): F[Boolean] =
      false.pure[F]

    override def obsPause(obsId: Observation.Id, reason: String): F[Boolean] =
      false.pure[F]

    override def obsStop(obsId: Observation.Id, reason: String): F[Boolean] =
      false.pure[F]

    override def datasetStartReadout(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      false.pure[F]

    override def datasetEndReadout(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      false.pure[F]

    override def datasetStartWrite(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      false.pure[F]

    override def datasetEndWrite(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      false.pure[F]

    override def visitStart[S](obsId: Observation.Id, staticCfg: S): F[Unit] =
      Applicative[F].unit

    override def atomStart(
      obsId:        Observation.Id,
      instrument:   Instrument,
      sequenceType: SequenceType,
      stepCount:    NonNegShort,
      generatedId:  Option[Atom.Id]
    ): F[Unit] = Applicative[F].unit

    override def getCurrentRecordedIds: F[ObsRecordedIds] = ObsRecordedIds.Empty.pure[F]
  }

  case class OdbCommandsImpl[F[_]](
    idTracker: Ref[F, ObsRecordedIds]
  )(using
    val F:     Sync[F],
    L:         Logger[F],
    client:    FetchClient[F, ObservationDB]
  ) extends OdbEventCommands[F]
      with IdTrackerOps[F](idTracker) {
    // given FetchClient[F, ObservationDB] = client

    private val fitsFileExtension                           = ".fits"
    private def normalizeFilename(fileName: String): String = if (
      fileName.endsWith(fitsFileExtension)
    ) fileName
    else fileName + fitsFileExtension

    override def visitStart[S](
      obsId:     Observation.Id,
      staticCfg: S
    ): F[Unit] = for {
      _   <- L.debug(s"Record visit for obsId: $obsId")
      vId <- recordVisit(obsId, staticCfg)
      -   <- setCurrentVisitId(obsId, vId.some)
    } yield ()

    override def atomStart(
      obsId:        Observation.Id,
      instrument:   Instrument,
      sequenceType: SequenceType,
      stepCount:    NonNegShort,
      generatedId:  Option[Atom.Id]
    ): F[Unit] = for {
      visitId <- getCurrentVisitId(obsId)
      _       <- L.debug(s"Record atom for obsId: $obsId and visitId: $visitId")
      atomId  <- recordAtom(visitId, sequenceType, stepCount, instrument, generatedId)
      -       <- setCurrentAtomId(obsId, atomId)
      _       <- AddAtomEventMutation[F]
                   .execute(atomId = atomId.value, stg = AtomStage.StartAtom)
      _       <- L.debug(s"New atom for obsId: $obsId aid: $atomId")
    } yield ()

    override def sequenceStart(
      obsId: Observation.Id
    ): F[Unit] = for {
      visitId <- getCurrentVisitId(obsId)
      _       <- L.debug(s"Send ODB event sequenceStart for obsId: $obsId, visitId: $visitId")
      _       <- AddSequenceEventMutation[F].execute(vId = visitId, cmd = SequenceCommand.Start)
      _       <- L.debug(s"ODB event sequenceStart sent for obsId: $obsId")
    } yield ()

    override def stepStartStep[D](
      obsId:           Observation.Id,
      dynamicConfig:   D,
      stepConfig:      StepConfig,
      telescopeConfig: CoreTelescopeConfig,
      observeClass:    ObserveClass,
      generatedId:     Option[Step.Id]
    ): F[Unit] =
      for {
        atomId <- getCurrentAtomId(obsId)
        stepId <-
          recordStep(atomId, dynamicConfig, stepConfig, telescopeConfig, observeClass, generatedId)
        _      <- setCurrentStepId(obsId, stepId.some)
        _      <- L.debug(s"Recorded step for obsId: $obsId, recordedStepId: $stepId")
        _      <- AddStepEventMutation[F]
                    .execute(stepId = stepId.value, stg = StepStage.StartStep)
        _      <- L.debug(s"ODB event stepStartStep sent with stepId $stepId")
      } yield ()

    override def stepStartConfigure(obsId: Observation.Id): F[Unit] =
      for {
        stepId <- getCurrentStepId(obsId)
        _      <- AddStepEventMutation[F].execute(stepId = stepId.value, stg = StepStage.StartConfigure)
        _      <- L.debug(s"ODB event stepStartConfigure sent with stepId ${stepId.value}")
      } yield ()

    override def stepEndConfigure(obsId: Observation.Id): F[Boolean] =
      for {
        stepId <- getCurrentStepId(obsId)
        _      <- L.debug(s"Send ODB event stepEndConfigure for obsId: $obsId, step $stepId")
        _      <- AddStepEventMutation[F].execute(stepId = stepId.value, stg = StepStage.EndConfigure)
        _      <- L.debug("ODB event stepEndConfigure sent")
      } yield true

    override def stepStartObserve(obsId: Observation.Id): F[Boolean] =
      for {
        stepId <- getCurrentStepId(obsId)
        _      <- L.debug(s"Send ODB event stepStartObserve for obsId: $obsId, step $stepId")
        _      <- AddStepEventMutation[F].execute(stepId = stepId.value, stg = StepStage.StartObserve)
        _      <- L.debug("ODB event stepStartObserve sent")
      } yield true

    override def datasetStartExposure(
      obsId:  Observation.Id,
      fileId: ImageFileId
    ): F[RecordDatasetMutation.Data.RecordDataset.Dataset] =
      for {
        stepId  <- getCurrentStepId(obsId)
        _       <-
          L.debug(
            s"Send ODB event datasetStartExposure for obsId: $obsId, stepId: $stepId with fileId: $fileId"
          )
        dataset <- recordDataset(stepId, fileId)
        _       <- setCurrentDatasetId(obsId, fileId, dataset.id.some)
        _       <- L.debug(s"Recorded dataset id ${dataset.id}")
        _       <- AddDatasetEventMutation[F]
                     .execute(datasetId = dataset.id, stg = DatasetStage.StartExpose)
        _       <- L.debug("ODB event datasetStartExposure sent")
      } yield dataset

    override def datasetEndExposure(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      for {
        datasetId <- getCurrentDatasetId(obsId, fileId)
        _         <- L.debug(s"Send ODB event datasetEndExposure for obsId: $obsId datasetId: $datasetId")
        _         <- AddDatasetEventMutation[F]
                       .execute(datasetId = datasetId, stg = DatasetStage.EndExpose)
        _         <- L.debug("ODB event datasetEndExposure sent")
      } yield true

    override def datasetStartReadout(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      for {
        datasetId <- getCurrentDatasetId(obsId, fileId)
        _         <- L.debug(s"Send ODB event datasetStartReadout for obsId: $obsId datasetId: $datasetId")
        _         <- AddDatasetEventMutation[F]
                       .execute(datasetId = datasetId, stg = DatasetStage.StartReadout)
        _         <- L.debug("ODB event datasetStartReadout sent")
      } yield true

    override def datasetEndReadout(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      for {
        datasetId <- getCurrentDatasetId(obsId, fileId)
        _         <- L.debug(s"Send ODB event datasetEndReadout for obsId: $obsId datasetId: $datasetId")
        _         <- AddDatasetEventMutation[F]
                       .execute(datasetId = datasetId, stg = DatasetStage.EndReadout)
        _         <- L.debug("ODB event datasetEndReadout sent")
      } yield true

    override def datasetStartWrite(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      for {
        datasetId <- getCurrentDatasetId(obsId, fileId)
        _         <- L.debug(s"Send ODB event datasetStartWrite for obsId: $obsId datasetId: $datasetId")
        _         <- AddDatasetEventMutation[F]
                       .execute(datasetId = datasetId, stg = DatasetStage.StartWrite)
        _         <- L.debug("ODB event datasetStartWrite sent")
      } yield true

    override def datasetEndWrite(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      for {
        datasetId <- getCurrentDatasetId(obsId, fileId)
        _         <- L.debug(s"Send ODB event datasetEndWrite for obsId: $obsId datasetId: $datasetId")
        _         <- AddDatasetEventMutation[F]
                       .execute(datasetId = datasetId, stg = DatasetStage.EndWrite)
        _         <- setCurrentDatasetId(obsId, fileId, none)
        _         <- L.debug("ODB event datasetEndWrite sent")
      } yield true

    override def stepEndObserve(obsId: Observation.Id): F[Boolean] =
      for {
        stepId <- getCurrentStepId(obsId)
        _      <- L.debug(s"Send ODB event stepEndConfigure for obsId: $obsId, step $stepId")
        _      <- AddStepEventMutation[F].execute(stepId = stepId.value, stg = StepStage.EndObserve)
        _      <- L.debug("ODB event stepEndObserve sent")
      } yield true

    override def stepEndStep(obsId: Observation.Id): F[Boolean] =
      for {
        stepId <- getCurrentStepId(obsId)
        _      <- L.debug(s"Send ODB event stepEndStep for obsId: $obsId, step $stepId")
        _      <- AddStepEventMutation[F].execute(stepId = stepId.value, stg = StepStage.EndStep)
        _      <- setCurrentStepId(obsId, none)
        _      <- L.debug("ODB event stepEndStep sent")
      } yield true

    override def stepAbort(obsId: Observation.Id): F[Boolean] =
      for {
        stepId <- getCurrentStepId(obsId)
        _      <- L.debug(s"Send ODB event stepAbort for obsId: $obsId, step $stepId")
        _      <- AddStepEventMutation[F].execute(stepId = stepId.value, stg = StepStage.Abort)
        _      <- setCurrentStepId(obsId, none)
        _      <- L.debug("ODB event stepAbort sent")
      } yield true

    override def stepStop(obsId: Observation.Id): F[Boolean] =
      for {
        stepId <- getCurrentStepId(obsId)
        _      <- L.debug(s"Send ODB event stepStop for obsId: $obsId, step $stepId")
        _      <- AddStepEventMutation[F].execute(stepId = stepId.value, stg = StepStage.Stop)
        _      <- L.debug("ODB event stepStop sent")
      } yield true

    override def atomEnd(obsId: Observation.Id): F[Boolean] =
      for {
        atomId <- getCurrentAtomId(obsId)
        _      <- L.debug(s"Send ODB event atomEnd for obsId: $obsId, atomId: $atomId")
        _      <- AddAtomEventMutation[F].execute(atomId = atomId.value, stg = AtomStage.EndAtom)
        _      <- L.debug("ODB event atomEnd sent")
      } yield true

    override def obsContinue(obsId: Observation.Id): F[Boolean] =
      for {
        _       <- L.debug(s"Send ODB event observationContinue for obsId: $obsId")
        visitId <- getCurrentVisitId(obsId)
        _       <- AddSequenceEventMutation[F].execute(vId = visitId, cmd = SequenceCommand.Continue)
        _       <- L.debug("ODB event observationContinue sent")
      } yield true

    override def obsPause(obsId: Observation.Id, reason: String): F[Boolean] =
      for {
        _       <- L.debug(s"Send ODB event observationPause for obsId: $obsId")
        visitId <- getCurrentVisitId(obsId)
        _       <- AddSequenceEventMutation[F].execute(vId = visitId, cmd = SequenceCommand.Pause)
        _       <- L.debug("ODB event observationPause sent")
      } yield true

    override def obsStop(obsId: Observation.Id, reason: String): F[Boolean] =
      for {
        _       <- L.debug(s"Send ODB event observationStop for obsId: $obsId")
        visitId <- getCurrentVisitId(obsId)
        _       <- AddSequenceEventMutation[F].execute(vId = visitId, cmd = SequenceCommand.Stop)
        _       <- setCurrentVisitId(obsId, none)
        _       <- L.debug("ODB event observationStop sent")
      } yield true

    private def recordVisit[S](
      obsId:     Observation.Id,
      staticCfg: S
    ): F[VisitId] = staticCfg match
      case s: gmos.StaticConfig.GmosNorth => recordGmosNorthVisit(obsId, s)
      case s: gmos.StaticConfig.GmosSouth => recordGmosSouthVisit(obsId, s)
      case s: Flamingos2StaticConfig      => recordFlamingos2Visit(obsId, s)

    private def recordGmosNorthVisit(
      obsId:     Observation.Id,
      staticCfg: gmos.StaticConfig.GmosNorth
    ): F[VisitId] =
      RecordGmosNorthVisitMutation[F]
        .execute(obsId, staticCfg.toInput)
        .raiseGraphQLErrors
        .map(_.recordGmosNorthVisit.visit.id)

    private def recordGmosSouthVisit(
      obsId:     Observation.Id,
      staticCfg: gmos.StaticConfig.GmosSouth
    ): F[VisitId] =
      RecordGmosSouthVisitMutation[F]
        .execute(obsId, staticCfg.toInput)
        .raiseGraphQLErrors
        .map(_.recordGmosSouthVisit.visit.id)

    private def recordFlamingos2Visit(
      obsId:     Observation.Id,
      staticCfg: Flamingos2StaticConfig
    ): F[VisitId] =
      RecordFlamingos2VisitMutation[F]
        .execute(obsId, staticCfg.toInput)
        .raiseGraphQLErrors
        .map(_.recordFlamingos2Visit.visit.id)

    private def recordAtom(
      visitId:      Visit.Id,
      sequenceType: SequenceType,
      stepCount:    NonNegShort,
      instrument:   Instrument,
      generatedId:  Option[Atom.Id]
    ): F[RecordedAtomId] =
      RecordAtomMutation[F]
        .execute:
          RecordAtomInput(visitId, instrument, sequenceType, stepCount.assign, generatedId.orIgnore)
        .raiseGraphQLErrors
        .map(_.recordAtom.atomRecord.id)
        .map(RecordedAtomId(_))

    private def recordStep[D](
      atomId:          RecordedAtomId,
      dynamicConfig:   D,
      stepConfig:      StepConfig,
      telescopeConfig: TelescopeConfig,
      observeClass:    ObserveClass,
      generatedId:     Option[Step.Id]
    ): F[RecordedStepId] = dynamicConfig match {
      case s @ gmos.DynamicConfig.GmosNorth(_, _, _, _, _, _, _)  =>
        recordGmosNorthStep:
          RecordGmosNorthStepInput(
            atomId.value,
            s.toInput,
            stepConfig.toInput,
            telescopeConfig.toInput.assign,
            observeClass,
            generatedId.orIgnore
          )
      case s @ gmos.DynamicConfig.GmosSouth(_, _, _, _, _, _, _)  =>
        recordGmosSouthStep:
          RecordGmosSouthStepInput(
            atomId.value,
            s.toInput,
            stepConfig.toInput,
            telescopeConfig.toInput.assign,
            observeClass,
            generatedId.orIgnore
          )
      case s @ Flamingos2DynamicConfig(_, _, _, _, _, _, _, _, _) =>
        recordFlamingos2Step:
          RecordFlamingos2StepInput(
            atomId.value,
            s.toInput,
            stepConfig.toInput,
            telescopeConfig.toInput.assign,
            observeClass,
            generatedId.orIgnore
          )
    }

    private def recordGmosNorthStep(input: RecordGmosNorthStepInput): F[RecordedStepId] =
      RecordGmosNorthStepMutation[F]
        .execute(input)
        .raiseGraphQLErrors
        .map(_.recordGmosNorthStep.stepRecord.id)
        .map(RecordedStepId(_))

    private def recordGmosSouthStep(input: RecordGmosSouthStepInput): F[RecordedStepId] =
      RecordGmosSouthStepMutation[F]
        .execute(input)
        .raiseGraphQLErrors
        .map(_.recordGmosSouthStep.stepRecord.id)
        .map(RecordedStepId(_))

    private def recordFlamingos2Step(input: RecordFlamingos2StepInput): F[RecordedStepId] =
      RecordFlamingos2StepMutation[F]
        .execute(input)
        .raiseGraphQLErrors
        .map(_.recordFlamingos2Step.stepRecord.id)
        .map(RecordedStepId(_))

    private def recordDataset(
      stepId: RecordedStepId,
      fileId: ImageFileId
    ): F[RecordDatasetMutation.Data.RecordDataset.Dataset] =
      Sync[F]
        .delay(Dataset.Filename.parse(normalizeFilename(fileId.value)).get)
        .flatMap: fileName =>
          RecordDatasetMutation[F]
            .execute(stepId.value, fileName)
            .raiseGraphQLErrors
            .map(_.recordDataset.dataset)

    override def getCurrentRecordedIds: F[ObsRecordedIds] = idTracker.get
  }

  class DummyOdbProxy[F[_]: Sync] extends OdbProxy[F] {
    val evCmds = new DummyOdbCommands[F]

    override def read(oid: Observation.Id): F[ObsQuery.Data.Observation] =
      MonadThrow[F]
        .raiseError(ObserveFailure.Unexpected("TestOdbProxy.read: Not implemented."))

    override def resetAcquisition(obsId: Observation.Id): F[Unit] =
      MonadThrow[F]
        .raiseError(ObserveFailure.Unexpected("TestOdbProxy.resetAcquisition: Not implemented."))

    override def obsEditSubscription(obsId: Observation.Id): Resource[F, fs2.Stream[F, Unit]] =
      Resource.eval(
        MonadThrow[F].raiseError(
          ObserveFailure.Unexpected("TestOdbProxy.obsEditSubscription: Not implemented")
        )
      )

    export evCmds.{
      datasetEndExposure,
      datasetEndReadout,
      datasetEndWrite,
      datasetStartExposure,
      datasetStartReadout,
      datasetStartWrite,
      obsContinue,
      obsPause,
      obsStop,
      sequenceStart,
      stepStop
    }

    override def stepStartStep[D](
      obsId:           Observation.Id,
      dynamicConfig:   D,
      stepConfig:      StepConfig,
      telescopeConfig: CoreTelescopeConfig,
      observeClass:    ObserveClass,
      generatedId:     Option[Step.Id]
    ): F[Unit] = Applicative[F].unit

    override def stepStartConfigure(obsId: Observation.Id): F[Unit] = Applicative[F].unit

    override def stepEndConfigure(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def stepStartObserve(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def stepEndObserve(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def stepEndStep(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def stepAbort(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def atomEnd(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def visitStart[S](obsId: Observation.Id, staticCfg: S): F[Unit] =
      Applicative[F].unit

    override def atomStart(
      obsId:        Observation.Id,
      instrument:   Instrument,
      sequenceType: SequenceType,
      stepCount:    NonNegShort,
      generatedId:  Option[Atom.Id]
    ): F[Unit] = Applicative[F].unit

    override def getCurrentRecordedIds: F[ObsRecordedIds] = ObsRecordedIds.Empty.pure[F]
  }

}
