// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.Applicative
import cats.MonadThrow
import cats.effect.Sync
import cats.effect.kernel.Ref
import cats.syntax.all.*
import clue.ClientAppliedF.*
import clue.FetchClient
import clue.data.syntax.*
import eu.timepit.refined.types.numeric.NonNegShort
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
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.StaticConfig
import lucuma.schemas.ObservationDB
import lucuma.schemas.ObservationDB.Scalars.DatasetId
import lucuma.schemas.ObservationDB.Scalars.VisitId
import lucuma.schemas.odb.input.*
import observe.common.ObsQueriesGQL.*
import observe.model.dhs.*
import observe.server.ObserveFailure
import observe.server.given
import org.typelevel.log4cats.Logger

sealed trait OdbEventCommands[F[_]] {
  def visitStart(
    obsId:     Observation.Id,
    staticCfg: StaticConfig
  ): F[Unit]
  def sequenceStart(
    obsId: Observation.Id
  ): F[Unit]
  def atomStart(
    obsId:        Observation.Id,
    instrument:   Instrument,
    sequenceType: SequenceType,
    stepCount:    NonNegShort
  ): F[Unit]
  def stepStartStep(
    obsId:         Observation.Id,
    dynamicConfig: DynamicConfig,
    stepConfig:    StepConfig,
    observeClass:  ObserveClass
  ): F[Unit]
  def stepStartConfigure(obsId:   Observation.Id): F[Unit]
  def stepEndConfigure(obsId:     Observation.Id): F[Boolean]
  def stepStartObserve(obsId:     Observation.Id): F[Boolean]
  def datasetStartExposure(obsId: Observation.Id, fileId: ImageFileId): F[Boolean]
  def datasetEndExposure(obsId:   Observation.Id, fileId: ImageFileId): F[Boolean]
  def datasetStartReadout(obsId:  Observation.Id, fileId: ImageFileId): F[Boolean]
  def datasetEndReadout(obsId:    Observation.Id, fileId: ImageFileId): F[Boolean]
  def datasetStartWrite(obsId:    Observation.Id, fileId: ImageFileId): F[Boolean]
  def datasetEndWrite(obsId:      Observation.Id, fileId: ImageFileId): F[Boolean]
  def stepEndObserve(obsId:       Observation.Id): F[Boolean]
  def stepEndStep(obsId:          Observation.Id): F[Boolean]
  def sequenceEnd(obsId:          Observation.Id): F[Boolean]
  def obsAbort(obsId:             Observation.Id, reason: String): F[Boolean]
  def obsContinue(obsId:          Observation.Id): F[Boolean]
  def obsPause(obsId:             Observation.Id, reason: String): F[Boolean]
  def obsStop(obsId:              Observation.Id, reason: String): F[Boolean]

}

trait OdbProxy[F[_]] extends OdbEventCommands[F] {
  def read(oid: Observation.Id): F[ObsQuery.Data.Observation]
  def queuedSequences: F[List[Observation.Id]]
}

object OdbProxy {
  def apply[F[_]](
    evCmds: OdbEventCommands[F]
  )(using Sync[F], FetchClient[F, ObservationDB]): OdbProxy[F] =
    new OdbProxy[F] {
      def read(oid: Observation.Id): F[ObsQuery.Data.Observation] =
        ObsQuery[F]
          .query(oid)
          .flatMap(
            _.observation.fold(
              Sync[F].raiseError[ObsQuery.Data.Observation](
                ObserveFailure.Unexpected(s"OdbProxy: Unable to read observation $oid")
              )
            )(
              _.pure[F]
            )
          )

      override def queuedSequences: F[List[Observation.Id]] =
        ActiveObservationIdsQuery[F]
          .query()
          .map(_.observations.matches.map(_.id))

      export evCmds.*
    }

  class DummyOdbCommands[F[_]: Applicative] extends OdbEventCommands[F] {
    override def sequenceStart(
      obsId: Observation.Id
    ): F[Unit] =
      ().pure[F]

    override def stepStartStep(
      obsId:         Observation.Id,
      dynamicConfig: DynamicConfig,
      stepConfig:    StepConfig,
      observeClass:  ObserveClass
    ): F[Unit] = ().pure[F]

    override def stepStartConfigure(obsId: Observation.Id): F[Unit] = Applicative[F].unit

    override def stepEndConfigure(obsId: Observation.Id): F[Boolean] =
      false.pure[F]

    override def stepStartObserve(obsId: Observation.Id): F[Boolean] =
      false.pure[F]

    override def datasetStartExposure(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      true.pure[F]

    override def datasetEndExposure(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      true.pure[F]

    override def stepEndObserve(obsId: Observation.Id): F[Boolean] =
      false.pure[F]

    override def stepEndStep(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def sequenceEnd(obsId: Observation.Id): F[Boolean] =
      false.pure[F]

    override def obsAbort(obsId: Observation.Id, reason: String): F[Boolean] =
      false.pure[F]

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

    override def visitStart(obsId: Observation.Id, staticCfg: StaticConfig): F[Unit] =
      Applicative[F].unit

    override def atomStart(
      obsId:        Observation.Id,
      instrument:   Instrument,
      sequenceType: SequenceType,
      stepCount:    NonNegShort
    ): F[Unit] = Applicative[F].unit
  }

  case class OdbCommandsImpl[F[_]](
    client:    FetchClient[F, ObservationDB],
    idTracker: Ref[F, ObsRecordedIds]
  )(using
    val F:     Sync[F],
    L:         Logger[F]
  ) extends OdbEventCommands[F]
      with IdTrackerOps[F](idTracker) {
    given FetchClient[F, ObservationDB] = client

    private val fitsFileExtension                           = ".fits"
    private def normalizeFilename(fileName: String): String = if (
      fileName.endsWith(fitsFileExtension)
    ) fileName
    else fileName + fitsFileExtension

    override def visitStart(
      obsId:     Observation.Id,
      staticCfg: StaticConfig
    ): F[Unit] = for {
      _   <- L.debug(s"Record visit for obsId: $obsId")
      vId <- recordVisit(obsId, staticCfg)
      -   <- setCurrentVisitId(obsId, vId.some)
    } yield ()

    override def atomStart(
      obsId:        Observation.Id,
      instrument:   Instrument,
      sequenceType: SequenceType,
      stepCount:    NonNegShort
    ): F[Unit] = for {
      visitId <- getCurrentVisitId(obsId)
      _       <- L.debug(s"Record atom for obsId: $obsId and visitId: $visitId")
      atomId  <- recordAtom(visitId, sequenceType, stepCount, instrument)
      -       <- setCurrentAtomId(obsId, atomId)
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

    override def stepStartStep(
      obsId:         Observation.Id,
      dynamicConfig: DynamicConfig,
      stepConfig:    StepConfig,
      observeClass:  ObserveClass
    ): F[Unit] =
      for {
        atomId <- getCurrentAtomId(obsId)
        stepId <- recordStep(atomId, dynamicConfig, stepConfig, observeClass)
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
        _      <- L.debug(s"Send ODB event stepStartConfigure for obsId: $obsId, step $stepId")
        _      <- AddStepEventMutation[F].execute(stepId = stepId.value, stg = StepStage.StartObserve)
        _      <- L.debug("ODB event stepStartObserve sent")
      } yield true

    override def datasetStartExposure(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      for {
        stepId    <- getCurrentStepId(obsId)
        _         <-
          L.debug(
            s"Send ODB event datasetStartExposure for obsId: $obsId, stepId: $stepId with fileId: $fileId"
          )
        datasetId <- recordDataset(stepId, fileId)
        _         <- setCurrentDatasetId(obsId, fileId, datasetId.some)
        _         <- L.debug(s"Recorded dataset id $datasetId")
        _         <- AddDatasetEventMutation[F]
                       .execute(datasetId = datasetId, stg = DatasetStage.StartObserve)
        _         <- L.debug("ODB event datasetStartExposure sent")
      } yield true

    override def datasetEndExposure(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      for {
        datasetId <- getCurrentDatasetId(obsId, fileId)
        _         <- L.debug(s"Send ODB event datasetEndExposure for obsId: $obsId datasetId: $datasetId")
        _         <- AddDatasetEventMutation[F]
                       .execute(datasetId = datasetId, stg = DatasetStage.EndObserve)
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

    override def sequenceEnd(obsId: Observation.Id): F[Boolean] =
      for {
        _ <- setCurrentVisitId(obsId, none)
        _ <- L.debug(s"Skipped sending ODB event sequenceEnd for obsId: $obsId")
      } yield true

    override def obsAbort(obsId: Observation.Id, reason: String): F[Boolean] =
      for {
        visitId <- getCurrentVisitId(obsId)
        _       <- L.debug(s"Send ODB event observationAbort for obsId: $obsId")
        _       <- AddSequenceEventMutation[F].execute(vId = visitId, cmd = SequenceCommand.Abort)
        _       <- setCurrentVisitId(obsId, none)
        _       <- L.debug("ODB event observationAbort sent")
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

    private def recordVisit(
      obsId:     Observation.Id,
      staticCfg: StaticConfig
    ): F[VisitId] = staticCfg match {
      case s: StaticConfig.GmosNorth => recordGmosNorthVisit(obsId, s)
      case s: StaticConfig.GmosSouth => recordGmosSouthVisit(obsId, s)
    }

    private def recordGmosNorthVisit(
      obsId:     Observation.Id,
      staticCfg: StaticConfig.GmosNorth
    ): F[VisitId] =
      RecordGmosNorthVisitMutation[F]
        .execute(obsId, staticCfg.toInput)
        .map(_.recordGmosNorthVisit.visit.id)

    private def recordGmosSouthVisit(
      obsId:     Observation.Id,
      staticCfg: StaticConfig.GmosSouth
    ): F[VisitId] =
      RecordGmosSouthVisitMutation[F]
        .execute(obsId, staticCfg.toInput)
        .map(_.recordGmosSouthVisit.visit.id)

    private def recordAtom(
      visitId:      Visit.Id,
      sequenceType: SequenceType,
      stepCount:    NonNegShort,
      instrument:   Instrument
    ): F[RecordedAtomId] =
      RecordAtomMutation[F]
        .execute(visitId, instrument.assign, sequenceType, stepCount)
        .map(_.recordAtom.atomRecord.id)
        .map(RecordedAtomId(_))

    private def recordStep(
      atomId:        RecordedAtomId,
      dynamicConfig: DynamicConfig,
      stepConfig:    StepConfig,
      observeClass:  ObserveClass
    ): F[RecordedStepId] = dynamicConfig match {
      case s: DynamicConfig.GmosNorth => recordGmosNorthStep(atomId, s, stepConfig, observeClass)
      case s: DynamicConfig.GmosSouth => recordGmosSouthStep(atomId, s, stepConfig, observeClass)
    }

    private def recordGmosNorthStep(
      atomId:        RecordedAtomId,
      dynamicConfig: DynamicConfig.GmosNorth,
      stepConfig:    StepConfig,
      observeClass:  ObserveClass
    ): F[RecordedStepId] =
      RecordGmosNorthStepMutation[F]
        .execute(atomId.value, dynamicConfig.toInput, stepConfig.toInput, observeClass)
        .map(_.recordGmosNorthStep.stepRecord.id)
        .map(RecordedStepId(_))

    private def recordGmosSouthStep(
      atomId:        RecordedAtomId,
      dynamicConfig: DynamicConfig.GmosSouth,
      stepConfig:    StepConfig,
      observeClass:  ObserveClass
    ): F[RecordedStepId] =
      RecordGmosSouthStepMutation[F]
        .execute(atomId.value, dynamicConfig.toInput, stepConfig.toInput, observeClass)
        .map(_.recordGmosSouthStep.stepRecord.id)
        .map(RecordedStepId(_))

    private def recordDataset(stepId: RecordedStepId, fileId: ImageFileId): F[DatasetId] =
      RecordDatasetMutation[F]
        .execute(stepId.value, normalizeFilename(fileId.value))
        .map(_.recordDataset.dataset.id)

  }

  class DummyOdbProxy[F[_]: MonadThrow] extends OdbProxy[F] {
    val evCmds = new DummyOdbCommands[F]

    override def read(oid: Observation.Id): F[ObsQuery.Data.Observation] = MonadThrow[F]
      .raiseError(ObserveFailure.Unexpected("TestOdbProxy.read: Not implemented."))

    override def queuedSequences: F[List[Observation.Id]] = List.empty[Observation.Id].pure[F]

    export evCmds.{
      datasetEndExposure,
      datasetEndReadout,
      datasetEndWrite,
      datasetStartExposure,
      datasetStartReadout,
      datasetStartWrite,
      obsAbort,
      obsContinue,
      obsPause,
      obsStop,
      sequenceEnd,
      sequenceStart
    }

    override def stepStartStep(
      obsId:         Observation.Id,
      dynamicConfig: DynamicConfig,
      stepConfig:    StepConfig,
      observeClass:  ObserveClass
    ): F[Unit] = Applicative[F].unit

    override def stepStartConfigure(obsId: Observation.Id): F[Unit] = Applicative[F].unit

    override def stepEndConfigure(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def stepStartObserve(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def stepEndObserve(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def stepEndStep(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def visitStart(obsId: Observation.Id, staticCfg: StaticConfig): F[Unit] =
      Applicative[F].unit

    override def atomStart(
      obsId:        Observation.Id,
      instrument:   Instrument,
      sequenceType: SequenceType,
      stepCount:    NonNegShort
    ): F[Unit] = Applicative[F].unit
  }

}
