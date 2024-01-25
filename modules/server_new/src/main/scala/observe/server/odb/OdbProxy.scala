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
import eu.timepit.refined.types.numeric.PosLong
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
import monocle.Iso
import monocle.Lens
import observe.common.ObsQueriesGQL.*
import observe.model.dhs.*
import observe.server.ObserveFailure
import observe.server.given
import org.typelevel.log4cats.Logger

import java.util.UUID
import scala.annotation.unused

sealed trait OdbEventCommands[F[_]] {
  def datasetStart(obsId:       Observation.Id, fileId: ImageFileId): F[Boolean]
  def datasetComplete(
    datasetId: DatasetId,
    obsId:     Observation.Id,
    fileId:    ImageFileId
  ): F[Boolean]
  def obsAbort(obsId:           Observation.Id, reason: String): F[Boolean]
  def sequenceEnd(obsId:        Observation.Id): F[Boolean]
  def sequenceStart(
    obsId:        Observation.Id,
    instrument:   Instrument,
    sequenceType: SequenceType,
    stepCount:    NonNegShort,
    staticCfg:    StaticConfig
  ): F[(VisitId, RecordedAtomId)] // TODO Return F[Unit]
  def obsContinue(obsId:        Observation.Id): F[Boolean]
  def obsPause(obsId:           Observation.Id, reason: String): F[Boolean]
  def obsStop(obsId:            Observation.Id, reason: String): F[Boolean]
  def stepStartStep(
    obsId:         Observation.Id,
    dynamicConfig: DynamicConfig,
    stepConfig:    StepConfig,
    observeClass:  ObserveClass
  ): F[RecordedStepId] // TODO No need to return this?
  def stepStartConfigure(obsId: Observation.Id): F[Unit]
  def stepEndConfigure(obsId:   Observation.Id): F[Boolean]
  def stepStartObserve(obsId:   Observation.Id): F[Boolean]
  def stepEndObserve(obsId:     Observation.Id): F[Boolean]
  def stepEndStep(obsId:        Observation.Id): F[Boolean]
}

sealed trait OdbProxy[F[_]] extends OdbEventCommands[F] {
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
    override def datasetStart(
      obsId:  Observation.Id,
      fileId: ImageFileId
    ): F[Boolean] = true.pure[F]

    override def datasetComplete(
      datasetId: DatasetId,
      obsId:     Observation.Id,
      fileId:    ImageFileId
    ): F[Boolean] = true.pure[F]

    override def obsAbort(
      obsId:  Observation.Id,
      reason: String
    ): F[Boolean] = false.pure[F]

    override def sequenceEnd(obsId: Observation.Id): F[Boolean] =
      false.pure[F]

    override def sequenceStart(
      obsId:        Observation.Id,
      instrument:   Instrument,
      sequenceType: SequenceType,
      stepCount:    NonNegShort,
      staticCfg:    StaticConfig
    ): F[(VisitId, RecordedAtomId)] =
      (Visit.Id(PosLong.unsafeFrom(12345678)), RecordedAtomId(Atom.Id.fromUuid(UUID.randomUUID())))
        .pure[F]

    override def obsContinue(obsId: Observation.Id): F[Boolean] =
      false.pure[F]

    override def obsPause(obsId: Observation.Id, reason: String): F[Boolean] =
      false.pure[F]

    override def obsStop(obsId: Observation.Id, reason: String): F[Boolean] =
      false.pure[F]

    override def stepStartStep(
      obsId:         Observation.Id,
      dynamicConfig: DynamicConfig,
      stepConfig:    StepConfig,
      observeClass:  ObserveClass
    ): F[RecordedStepId] = RecordedStepId(Step.Id.fromUuid(UUID.randomUUID())).pure[F]

    override def stepStartConfigure(obsId: Observation.Id): F[Unit] = Applicative[F].unit

    override def stepEndStep(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def stepEndConfigure(obsId: Observation.Id): F[Boolean] =
      false.pure[F]

    override def stepStartObserve(obsId: Observation.Id): F[Boolean] =
      false.pure[F]

    override def stepEndObserve(obsId: Observation.Id): F[Boolean] =
      false.pure[F]
  }

  type ObsRecordedIds = Map[Observation.Id, RecordedIds]
  object ObsRecordedIds:
    val Empty: ObsRecordedIds                                                = Map.empty
    def at(obsId: Observation.Id): Lens[ObsRecordedIds, Option[RecordedIds]] =
      Iso.id[ObsRecordedIds].at(obsId)

  case class OdbCommandsImpl[F[_]](
    client:    FetchClient[F, ObservationDB],
    idTracker: Ref[F, ObsRecordedIds]
  )(using
    val F:     Sync[F],
    L:         Logger[F]
  ) extends OdbEventCommands[F] {
    given FetchClient[F, ObservationDB] = client

    private def getCurrentVisitId(obsId: Observation.Id): F[Visit.Id] =
      idTracker.get
        .map:
          _.get(obsId)
            .flatMap(RecordedIds.visitId.get)
            .toRight(ObserveFailure.Unexpected(s"No current recorded visit for obsId [$obsId]"))
        .rethrow

    private def setCurrentVisitId(obsId: Observation.Id, visitId: Visit.Id): F[Unit] =
      idTracker.update:
        ObsRecordedIds.at(obsId).replace(RecordedIds(visitId.some, none, none).some)

    private def getCurrentAtomId(obsId: Observation.Id): F[RecordedAtomId] =
      idTracker.get
        .map:
          _.get(obsId)
            .flatMap(RecordedIds.atomId.get)
            .toRight(ObserveFailure.Unexpected(s"No current recorded atom for obsId [$obsId]"))
        .rethrow

    private def setCurrentAtomId(obsId: Observation.Id, atomId: RecordedAtomId): F[Unit] =
      idTracker.update:
        ObsRecordedIds.at(obsId).some.andThen(RecordedIds.atomId).replace(atomId.some)

    private def getCurrentStepId(obsId: Observation.Id): F[RecordedStepId] =
      idTracker.get
        .map:
          _.get(obsId)
            .flatMap(RecordedIds.stepId.get)
            .toRight(ObserveFailure.Unexpected(s"No current recorded step for obsId [$obsId]"))
        .rethrow

    private def setCurrentStepId(obsId: Observation.Id, stepId: RecordedStepId): F[Unit] =
      idTracker.update:
        ObsRecordedIds.at(obsId).some.andThen(RecordedIds.stepId).replace(stepId.some)

    private val fitsFileExtension                                   = ".fits"
    @unused private def normalizeFilename(fileName: String): String = if (
      fileName.endsWith(fitsFileExtension)
    ) fileName
    else fileName + fitsFileExtension

    override def datasetStart(
      obsId:  Observation.Id,
      fileId: ImageFileId
    ): F[Boolean] =
      for {
        stepId <- getCurrentStepId(obsId)
        _      <-
          L.debug(
            s"Send ODB event datasetStart for obsId: $obsId, stepId: $stepId with fileId: $fileId"
          )
        did    <- recordDataset(stepId, fileId)
        _      <- L.debug(s"Recorded dataset id $did")
        _      <- AddDatasetEventMutation[F]
                    .execute(datasetId = did, stg = DatasetStage.StartObserve)
        _      <- L.debug("ODB event datasetStart sent")
      } yield true

    private def recordDataset(stepId: RecordedStepId, fileId: ImageFileId): F[DatasetId] =
      RecordDatasetMutation[F]
        .execute(stepId.value, normalizeFilename(fileId.value))
        .map(_.recordDataset.dataset.id)

    override def datasetComplete(
      datasetId: DatasetId,
      obsId:     Observation.Id,
      fileId:    ImageFileId
    ): F[Boolean] =
      for {
        _ <-
          L.debug(
            s"Send ODB event datasetComplete for obsId: $obsId datasetId: $datasetId with fileId: $fileId"
          )
        // FIXME Data set id is null
        // _ <- AddDatasetEventMutation[F]
        //        .execute(datasetId = datasetId, stg = DatasetStage.EndObserve)
        _ <- L.debug("ODB event datasetComplete sent")
      } yield true

    override def obsAbort(
      obsId:  Observation.Id,
      reason: String
    ): F[Boolean] =
      for {
        visitId <- getCurrentVisitId(obsId)
        _       <- L.debug(s"Send ODB event observationAbort for obsId: $obsId")
        _       <- AddSequenceEventMutation[F].execute(vId = visitId, cmd = SequenceCommand.Abort)
        _       <- L.debug("ODB event observationAbort sent")
      } yield true

    override def sequenceEnd(obsId: Observation.Id): F[Boolean] =
      L.debug(s"Skipped sending ODB event sequenceEnd for obsId: $obsId")
        .as(true)

    override def sequenceStart(
      obsId:        Observation.Id,
      instrument:   Instrument,
      sequenceType: SequenceType,
      stepCount:    NonNegShort,
      staticCfg:    StaticConfig
    ): F[(VisitId, RecordedAtomId)] =
      for {
        // TODO Check that there are no current visits or atoms??
        _       <- L.debug(s"Record visit for obsId: $obsId")
        visitId <- recordVisit(obsId, staticCfg)
        -       <- setCurrentVisitId(obsId, visitId)
        _       <- L.debug(s"Record atom for obsId: $obsId and visitId: $visitId")
        atomId  <- recordAtom(visitId, sequenceType, stepCount, instrument)
        -       <- setCurrentAtomId(obsId, atomId)
        _       <- L.debug(s"New atom for obsId: $obsId aid: $atomId")
        _       <- L.debug(s"Send ODB event sequenceStart for obsId: $obsId, visitId: $visitId")
        _       <- AddSequenceEventMutation[F].execute(vId = visitId, cmd = SequenceCommand.Start)
        _       <- L.debug(s"ODB event sequenceStart sent for obsId: $obsId")
      } yield (visitId, atomId)

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
        _       <- L.debug("ODB event observationStop sent")
      } yield true

    override def stepStartStep(
      obsId:         Observation.Id,
      dynamicConfig: DynamicConfig,
      stepConfig:    StepConfig,
      observeClass:  ObserveClass
    ): F[RecordedStepId] =
      for {
        atomId <- getCurrentAtomId(obsId)
        stepId <- recordStep(atomId, dynamicConfig, stepConfig, observeClass)
        _      <- setCurrentStepId(obsId, stepId)
        _      <- L.debug(s"Recorded step for obsId: $obsId, recordedStepId: $stepId")
        _      <- AddStepEventMutation[F]
                    .execute(stepId = stepId.value, stg = StepStage.StartStep)
        _      <- L.debug(s"ODB event stepStartStep sent with stepId $stepId")
      } yield stepId

    override def stepStartConfigure(obsId: Observation.Id): F[Unit] =
      for {
        stepId <- getCurrentStepId(obsId)
        _      <- AddStepEventMutation[F].execute(stepId = stepId.value, stg = StepStage.StartConfigure)
        _      <- L.debug(s"ODB event stepStartConfigure sent with stepId ${stepId.value}")
      } yield ()

    override def stepEndStep(obsId: Observation.Id): F[Boolean] =
      for {
        stepId <- getCurrentStepId(obsId)
        _      <- L.debug(s"Send ODB event stepEndStep for obsId: $obsId, step $stepId")
        _      <- AddStepEventMutation[F].execute(stepId = stepId.value, stg = StepStage.EndStep)
        _      <- L.debug("ODB event stepEndStep sent")
      } yield true

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

    override def stepEndObserve(obsId: Observation.Id): F[Boolean] =
      for {
        stepId <- getCurrentStepId(obsId)
        _      <- L.debug(s"Send ODB event stepEndConfigure for obsId: $obsId, step $stepId")
        _      <- AddStepEventMutation[F].execute(stepId = stepId.value, stg = StepStage.EndObserve)
        _      <- L.debug("ODB event stepEndObserve sent")
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
      for
        visitId <- RecordGmosNorthVisitMutation[F]
                     .execute(obsId, staticCfg.toInput)
                     .map(_.recordGmosNorthVisit.visit.id)
        _       <- setCurrentVisitId(obsId, visitId)
      yield visitId

    private def recordGmosSouthVisit(
      obsId:     Observation.Id,
      staticCfg: StaticConfig.GmosSouth
    ): F[VisitId] =
      for
        visitId <- RecordGmosSouthVisitMutation[F]
                     .execute(obsId, staticCfg.toInput)
                     .map(_.recordGmosSouthVisit.visit.id)
        _       <- setCurrentVisitId(obsId, visitId)
      yield visitId

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
  }

  class TestOdbProxy[F[_]: MonadThrow] extends OdbProxy[F] {
    val evCmds = new DummyOdbCommands[F]

    override def read(oid: Observation.Id): F[ObsQuery.Data.Observation] = MonadThrow[F]
      .raiseError(ObserveFailure.Unexpected("TestOdbProxy.read: Not implemented."))

    override def queuedSequences: F[List[Observation.Id]] = List.empty[Observation.Id].pure[F]

    export evCmds.{
      datasetComplete,
      datasetStart,
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
    ): F[RecordedStepId] = RecordedStepId(Step.Id.fromUuid(UUID.randomUUID())).pure[F]

    override def stepStartConfigure(obsId: Observation.Id): F[Unit] = Applicative[F].unit

    override def stepEndStep(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def stepEndConfigure(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def stepStartObserve(obsId: Observation.Id): F[Boolean] = false.pure[F]

    override def stepEndObserve(obsId: Observation.Id): F[Boolean] = false.pure[F]
  }

}
