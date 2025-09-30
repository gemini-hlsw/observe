// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.Endo
import cats.effect.Sync
import cats.effect.kernel.Ref
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import clue.FetchClientWithPars
import clue.data.syntax.*
import clue.syntax.*
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
import lucuma.core.util.IdempotencyKey
import lucuma.schemas.ObservationDB
import lucuma.schemas.ObservationDB.Scalars.VisitId
import lucuma.schemas.ObservationDB.Types.RecordAtomInput
import lucuma.schemas.ObservationDB.Types.RecordFlamingos2StepInput
import lucuma.schemas.ObservationDB.Types.RecordGmosNorthStepInput
import lucuma.schemas.ObservationDB.Types.RecordGmosSouthStepInput
import lucuma.schemas.odb.input.*
import observe.common.EventsGQL.*
import observe.model.dhs.*
import observe.model.odb.ObsRecordedIds
import observe.model.odb.RecordedAtomId
import observe.model.odb.RecordedStepId
import org.http4s.Header
import org.http4s.Request
import org.http4s.headers.`Idempotency-Key`
import org.typelevel.log4cats.Logger

case class OdbCommandsImpl[F[_]: UUIDGen](
  idTracker: Ref[F, ObsRecordedIds]
)(using client: FetchClientWithPars[F, Request[F], ObservationDB])(using
  val F:     Sync[F],
  L:         Logger[F]
) extends OdbCommands[F]
    with IdTrackerOps[F](idTracker) {

  private val FitsFileExtension: String                   = ".fits"
  private def normalizeFilename(fileName: String): String =
    if (fileName.endsWith(FitsFileExtension)) fileName
    else fileName + FitsFileExtension

  private def newIdempotencyKey: F[IdempotencyKey] =
    UUIDGen[F].randomUUID.map(IdempotencyKey(_))

  // We use the default retry policy in the http4s client. For it to kick in
  // we need to add the `Idempotency-Key` header to non-GET requests.
  private def addIdempotencyKey(idempotencyKey: IdempotencyKey): Endo[Request[F]] = req =>
    req.putHeaders(`Idempotency-Key`(idempotencyKey.toString))

  override def visitStart[S](
    obsId:     Observation.Id,
    staticCfg: S
  ): F[Unit] =
    for
      _   <- L.debug(s"Record visit for obsId: [$obsId]")
      vId <- recordVisit(obsId, staticCfg)
      -   <- setCurrentVisitId(obsId, vId.some)
    yield ()

  private def atomStart(
    obsId:        Observation.Id,
    instrument:   Instrument,
    sequenceType: SequenceType,
    generatedId:  Atom.Id
  ): F[RecordedAtomId] =
    for
      visitId <- getCurrentVisitId(obsId)
      _       <-
        L.debug(s"Record atom for obsId: $obsId and visitId: $visitId - GeneratedId: $generatedId")
      atomId  <- recordAtom(visitId, sequenceType, instrument, generatedId)
      -       <- setCurrentAtomId(obsId, generatedId, atomId)
      _       <- L.debug(s"New atom recorded for obsId: $obsId - Recorded atomId: $atomId")
    yield atomId

  override def sequenceStart(
    obsId: Observation.Id
  ): F[Unit] = for
    visitId        <- getCurrentVisitId(obsId)
    _              <- L.debug(s"Send ODB event sequenceStart for obsId: $obsId, visitId: $visitId")
    idempotencyKey <- newIdempotencyKey
    _              <-
      AddSequenceEventMutation[F]
        .execute(visitId, SequenceCommand.Start, idempotencyKey, addIdempotencyKey(idempotencyKey))
    _              <- L.debug(s"ODB event sequenceStart sent for obsId: $obsId")
  yield ()

  override def stepStartStep[D](
    obsId:           Observation.Id,
    dynamicConfig:   D,
    stepConfig:      StepConfig,
    telescopeConfig: CoreTelescopeConfig,
    observeClass:    ObserveClass,
    generatedId:     Option[Step.Id],
    generatedAtomId: Atom.Id,
    instrument:      Instrument,
    sequenceType:    SequenceType
  ): F[Unit] =
    for
      atomId         <-
        getCurrentAtomId(obsId, generatedAtomId, atomStart(obsId, instrument, sequenceType, _))
      stepId         <-
        recordStep(atomId, dynamicConfig, stepConfig, telescopeConfig, observeClass, generatedId)
      _              <- setCurrentStepId(obsId, stepId.some)
      _              <- L.debug(s"Recorded step for obsId: $obsId, recordedStepId: $stepId")
      idempotencyKey <- newIdempotencyKey
      _              <- AddStepEventMutation[F]
                          .execute(
                            stepId.value,
                            StepStage.StartStep,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- L.debug(s"ODB event stepStartStep sent with stepId $stepId")
    yield ()

  override def stepStartConfigure(obsId: Observation.Id): F[Unit] =
    for
      stepId         <- getCurrentStepId(obsId)
      _              <- L.debug(s"Send ODB event stepStartConfigure for obsId: $obsId, step $stepId")
      idempotencyKey <- newIdempotencyKey
      _              <- AddStepEventMutation[F]
                          .execute(
                            stepId.value,
                            StepStage.StartConfigure,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- L.debug(s"ODB event stepStartConfigure sent with stepId ${stepId.value}")
    yield ()

  override def stepEndConfigure(obsId: Observation.Id): F[Boolean] =
    for
      stepId         <- getCurrentStepId(obsId)
      _              <- L.debug(s"Send ODB event stepEndConfigure for obsId: $obsId, step $stepId")
      idempotencyKey <- newIdempotencyKey
      _              <- AddStepEventMutation[F]
                          .execute(
                            stepId.value,
                            StepStage.EndConfigure,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- L.debug("ODB event stepEndConfigure sent")
    yield true

  override def stepStartObserve(obsId: Observation.Id): F[Boolean] =
    for
      stepId         <- getCurrentStepId(obsId)
      _              <- L.debug(s"Send ODB event stepStartObserve for obsId: $obsId, step $stepId")
      idempotencyKey <- newIdempotencyKey
      _              <- AddStepEventMutation[F]
                          .execute(
                            stepId.value,
                            StepStage.StartObserve,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- L.debug("ODB event stepStartObserve sent")
    yield true

  override def datasetStartExposure(
    obsId:  Observation.Id,
    fileId: ImageFileId
  ): F[RecordDatasetMutation.Data.RecordDataset.Dataset] =
    for
      stepId         <- getCurrentStepId(obsId)
      _              <-
        L.debug:
          s"Send ODB event datasetStartExposure for obsId: $obsId, stepId: $stepId with fileId: $fileId"
      dataset        <- recordDataset(stepId, fileId)
      _              <- setCurrentDatasetId(obsId, fileId, dataset.id.some)
      _              <- L.debug(s"Recorded dataset id ${dataset.id}")
      idempotencyKey <- newIdempotencyKey
      _              <- AddDatasetEventMutation[F]
                          .execute(
                            dataset.id,
                            DatasetStage.StartExpose,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- L.debug("ODB event datasetStartExposure sent")
    yield dataset

  override def datasetEndExposure(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
    for
      datasetId      <- getCurrentDatasetId(obsId, fileId)
      _              <- L.debug(s"Send ODB event datasetEndExposure for obsId: $obsId datasetId: $datasetId")
      idempotencyKey <- newIdempotencyKey
      _              <- AddDatasetEventMutation[F]
                          .execute(
                            datasetId,
                            DatasetStage.EndExpose,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- L.debug("ODB event datasetEndExposure sent")
    yield true

  override def datasetStartReadout(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
    for
      datasetId      <- getCurrentDatasetId(obsId, fileId)
      _              <- L.debug(s"Send ODB event datasetStartReadout for obsId: $obsId datasetId: $datasetId")
      idempotencyKey <- newIdempotencyKey
      _              <- AddDatasetEventMutation[F]
                          .execute(
                            datasetId,
                            DatasetStage.StartReadout,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- L.debug("ODB event datasetStartReadout sent")
    yield true

  override def datasetEndReadout(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
    for
      datasetId      <- getCurrentDatasetId(obsId, fileId)
      _              <- L.debug(s"Send ODB event datasetEndReadout for obsId: $obsId datasetId: $datasetId")
      idempotencyKey <- newIdempotencyKey
      _              <- AddDatasetEventMutation[F]
                          .execute(
                            datasetId,
                            DatasetStage.EndReadout,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- L.debug("ODB event datasetEndReadout sent")
    yield true

  override def datasetStartWrite(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
    for
      datasetId      <- getCurrentDatasetId(obsId, fileId)
      _              <- L.debug(s"Send ODB event datasetStartWrite for obsId: $obsId datasetId: $datasetId")
      idempotencyKey <- newIdempotencyKey
      _              <- AddDatasetEventMutation[F]
                          .execute(
                            datasetId,
                            DatasetStage.StartWrite,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- L.debug("ODB event datasetStartWrite sent")
    yield true

  override def datasetEndWrite(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
    for
      datasetId      <- getCurrentDatasetId(obsId, fileId)
      _              <- L.debug(s"Send ODB event datasetEndWrite for obsId: $obsId datasetId: $datasetId")
      idempotencyKey <- newIdempotencyKey
      _              <- AddDatasetEventMutation[F]
                          .execute(
                            datasetId,
                            DatasetStage.EndWrite,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- setCurrentDatasetId(obsId, fileId, none)
      _              <- L.debug("ODB event datasetEndWrite sent")
    yield true

  override def stepEndObserve(obsId: Observation.Id): F[Boolean] =
    for
      stepId         <- getCurrentStepId(obsId)
      _              <- L.debug(s"Send ODB event stepEndConfigure for obsId: $obsId, step $stepId")
      idempotencyKey <- newIdempotencyKey
      _              <- AddStepEventMutation[F]
                          .execute(
                            stepId.value,
                            StepStage.EndObserve,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- L.debug("ODB event stepEndObserve sent")
    yield true

  override def stepEndStep(obsId: Observation.Id): F[Boolean] =
    for
      stepId         <- getCurrentStepId(obsId)
      _              <- L.debug(s"Send ODB event stepEndStep for obsId: $obsId, step $stepId")
      idempotencyKey <- newIdempotencyKey
      _              <- AddStepEventMutation[F]
                          .execute(
                            stepId.value,
                            StepStage.EndStep,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- setCurrentStepId(obsId, none)
      _              <- L.debug("ODB event stepEndStep sent")
    yield true

  override def stepAbort(obsId: Observation.Id): F[Boolean] =
    for
      stepId         <- getCurrentStepId(obsId)
      _              <- L.debug(s"Send ODB event stepAbort for obsId: $obsId, step $stepId")
      idempotencyKey <- newIdempotencyKey
      _              <-
        AddStepEventMutation[F]
          .execute(stepId.value, StepStage.Abort, idempotencyKey, addIdempotencyKey(idempotencyKey))
      _              <- setCurrentStepId(obsId, none)
      _              <- L.debug("ODB event stepAbort sent")
    yield true

  override def stepStop(obsId: Observation.Id): F[Boolean] =
    for
      stepId         <- getCurrentStepId(obsId)
      _              <- L.debug(s"Send ODB event stepStop for obsId: $obsId, step $stepId")
      idempotencyKey <- newIdempotencyKey
      _              <-
        AddStepEventMutation[F]
          .execute(stepId.value, StepStage.Stop, idempotencyKey, addIdempotencyKey(idempotencyKey))
      _              <- L.debug("ODB event stepStop sent")
    yield true

  override def obsContinue(obsId: Observation.Id): F[Boolean] =
    for
      _              <- L.debug(s"Send ODB event observationContinue for obsId: $obsId")
      visitId        <- getCurrentVisitId(obsId)
      idempotencyKey <- newIdempotencyKey
      _              <- AddSequenceEventMutation[F]
                          .execute(
                            visitId,
                            SequenceCommand.Continue,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- L.debug("ODB event observationContinue sent")
    yield true

  override def obsPause(obsId: Observation.Id, reason: String): F[Boolean] =
    for
      _              <- L.debug(s"Send ODB event observationPause for obsId: $obsId")
      visitId        <- getCurrentVisitId(obsId)
      idempotencyKey <- newIdempotencyKey
      _              <- AddSequenceEventMutation[F]
                          .execute(
                            visitId,
                            SequenceCommand.Pause,
                            idempotencyKey,
                            addIdempotencyKey(idempotencyKey)
                          )
      _              <- L.debug("ODB event observationPause sent")
    yield true

  override def obsStop(obsId: Observation.Id, reason: String): F[Boolean] =
    for
      _              <- L.debug(s"Send ODB event observationStop for obsId: $obsId")
      visitId        <- getCurrentVisitId(obsId)
      idempotencyKey <- newIdempotencyKey
      _              <-
        AddSequenceEventMutation[F]
          .execute(visitId, SequenceCommand.Stop, idempotencyKey, addIdempotencyKey(idempotencyKey))
      _              <- setCurrentVisitId(obsId, none)
      _              <- L.debug("ODB event observationStop sent")
    yield true

  private def recordVisit[S](
    obsId:     Observation.Id,
    staticCfg: S
  ): F[VisitId] = staticCfg match
    case s @ gmos.StaticConfig.GmosNorth(_, _, _, _) => recordGmosNorthVisit(obsId, s)
    case s @ gmos.StaticConfig.GmosSouth(_, _, _, _) => recordGmosSouthVisit(obsId, s)
    case s @ Flamingos2StaticConfig(_, _)            => recordFlamingos2Visit(obsId, s)

  private def recordGmosNorthVisit(
    obsId:     Observation.Id,
    staticCfg: gmos.StaticConfig.GmosNorth
  ): F[VisitId] =
    newIdempotencyKey.flatMap: idempotencyKey =>
      RecordGmosNorthVisitMutation[F]
        .execute(obsId, staticCfg.toInput, idempotencyKey, addIdempotencyKey(idempotencyKey))
        .raiseGraphQLErrors
        .map(_.recordGmosNorthVisit.visit.id)

  private def recordGmosSouthVisit(
    obsId:     Observation.Id,
    staticCfg: gmos.StaticConfig.GmosSouth
  ): F[VisitId] =
    newIdempotencyKey.flatMap: idempotencyKey =>
      RecordGmosSouthVisitMutation[F]
        .execute(obsId, staticCfg.toInput, idempotencyKey, addIdempotencyKey(idempotencyKey))
        .raiseGraphQLErrors
        .map(_.recordGmosSouthVisit.visit.id)

  private def recordFlamingos2Visit(
    obsId:     Observation.Id,
    staticCfg: Flamingos2StaticConfig
  ): F[VisitId] =
    newIdempotencyKey.flatMap: idempotencyKey =>
      RecordFlamingos2VisitMutation[F]
        .execute(obsId, staticCfg.toInput, idempotencyKey, addIdempotencyKey(idempotencyKey))
        .raiseGraphQLErrors
        .map(_.recordFlamingos2Visit.visit.id)

  private def recordAtom(
    visitId:      Visit.Id,
    sequenceType: SequenceType,
    instrument:   Instrument,
    generatedId:  Atom.Id
  ): F[RecordedAtomId] =
    newIdempotencyKey.flatMap: idempotencyKey =>
      RecordAtomMutation[F]
        .execute(
          RecordAtomInput(
            visitId,
            instrument,
            sequenceType,
            generatedId.assign,
            idempotencyKey.assign
          ),
          addIdempotencyKey(idempotencyKey)
        )
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
    newIdempotencyKey.flatMap: idempotencyKey =>
      RecordGmosNorthStepMutation[F]
        .execute(
          RecordGmosNorthStepInput.idempotencyKey.replace(idempotencyKey.assign)(input),
          addIdempotencyKey(idempotencyKey)
        )
        .raiseGraphQLErrors
        .map(_.recordGmosNorthStep.stepRecord.id)
        .map(RecordedStepId(_))

  private def recordGmosSouthStep(input: RecordGmosSouthStepInput): F[RecordedStepId] =
    newIdempotencyKey.flatMap: idempotencyKey =>
      RecordGmosSouthStepMutation[F]
        .execute(
          RecordGmosSouthStepInput.idempotencyKey.replace(idempotencyKey.assign)(input),
          addIdempotencyKey(idempotencyKey)
        )
        .raiseGraphQLErrors
        .map(_.recordGmosSouthStep.stepRecord.id)
        .map(RecordedStepId(_))

  private def recordFlamingos2Step(input: RecordFlamingos2StepInput): F[RecordedStepId] =
    newIdempotencyKey.flatMap: idempotencyKey =>
      RecordFlamingos2StepMutation[F]
        .execute(
          RecordFlamingos2StepInput.idempotencyKey.replace(idempotencyKey.assign)(input),
          addIdempotencyKey(idempotencyKey)
        )
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
        newIdempotencyKey.flatMap: idempotencyKey =>
          RecordDatasetMutation[F]
            .execute(stepId.value, fileName, idempotencyKey, addIdempotencyKey(idempotencyKey))
            .raiseGraphQLErrors
            .map(_.recordDataset.dataset)

  override def getCurrentRecordedIds: F[ObsRecordedIds] = idTracker.get
}
