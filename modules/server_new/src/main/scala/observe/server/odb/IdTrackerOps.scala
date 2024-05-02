// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.MonadThrow
import cats.effect.Ref
import cats.syntax.all.*
import lucuma.core.model.Observation
import lucuma.core.model.Visit
import lucuma.core.model.sequence.Dataset
import observe.model.dhs.ImageFileId
import observe.model.odb.ObsRecordedIds
import observe.model.odb.RecordedAtom
import observe.model.odb.RecordedAtomId
import observe.model.odb.RecordedStep
import observe.model.odb.RecordedStepId
import observe.model.odb.RecordedVisit
import observe.server.ObserveFailure

trait IdTrackerOps[F[_]: MonadThrow](idTracker: Ref[F, ObsRecordedIds]):
  protected def getCurrentVisitId(obsId: Observation.Id): F[Visit.Id] =
    idTracker.get
      .map:
        ObsRecordedIds
          .at(obsId)
          .get(_)
          .map(observe.model.odb.RecordedVisit.visitId.get)
          .toRight(ObserveFailure.Unexpected(s"No current recorded visit for obsId [$obsId]"))
      .rethrow

  protected def setCurrentVisitId(obsId: Observation.Id, visitId: Option[Visit.Id]): F[Unit] =
    idTracker.update:
      ObsRecordedIds
        .at(obsId)
        .replace:
          visitId.map(RecordedVisit(_))
    // For the moment, we don't check if there's an existing visit, since there's no "visitEnd".
    //     .modify:
    //       case Some(staleVisitId) if visitId.isDefined =>
    //         throw ObserveFailure.Unexpected:
    //           s"Attempted to set visitId for [$obsId] when it was already set. " +
    //             s"Existing value [$staleVisitId], new attempted value [${visitId.get}]"
    //       case _                                       => visitId.map(RecordedVisit(_))

  // AtomId is never set to None, there's no "end atom" event in the engine.
  protected def getCurrentAtomId(obsId: Observation.Id): F[RecordedAtomId] =
    idTracker.get
      .map:
        ObsRecordedIds
          .at(obsId)
          .get(_)
          .flatMap(observe.model.odb.RecordedVisit.atomId.getOption)
          .toRight(ObserveFailure.Unexpected(s"No current recorded atom for obsId [$obsId]"))
      .rethrow

  protected def setCurrentAtomId(obsId: Observation.Id, atomId: RecordedAtomId): F[Unit] =
    idTracker.update:
      ObsRecordedIds
        .at(obsId)
        .some
        .andThen(observe.model.odb.RecordedVisit.atom)
        .replace(RecordedAtom(atomId).some)

  protected def getCurrentStepId(obsId: Observation.Id): F[RecordedStepId] =
    idTracker.get
      .map:
        ObsRecordedIds
          .at(obsId)
          .get(_)
          .flatMap(observe.model.odb.RecordedVisit.stepId.getOption)
          .toRight(ObserveFailure.Unexpected(s"No current recorded step for obsId [$obsId]"))
      .rethrow

  protected def setCurrentStepId(obsId: Observation.Id, stepId: Option[RecordedStepId]): F[Unit] =
    idTracker.update:
      ObsRecordedIds
        .at(obsId)
        .some
        .andThen(observe.model.odb.RecordedVisit.step)
        .replace(stepId.map(RecordedStep(_)))

  protected def getCurrentDatasetId(obsId: Observation.Id, fileId: ImageFileId): F[Dataset.Id] =
    idTracker.get
      .map:
        ObsRecordedIds
          .at(obsId)
          .get(_)
          .flatMap(observe.model.odb.RecordedVisit.datasetId(fileId).getOption)
          .flatten
          .toRight(ObserveFailure.Unexpected(s"No current recorded dataset for obsId [$obsId]"))
      .rethrow

  protected def setCurrentDatasetId(
    obsId:     Observation.Id,
    fileId:    ImageFileId,
    datasetId: Option[Dataset.Id]
  ): F[Unit] =
    idTracker.update:
      ObsRecordedIds
        .at(obsId)
        .some
        .andThen(observe.model.odb.RecordedVisit.datasetId(fileId))
        .modify:
          case Some(staleDatasetId) if datasetId.isDefined =>
            throw ObserveFailure.Unexpected:
              s"Attempted to set current datasetId for [$obsId] when it was already set. " +
                s"Existing value [$staleDatasetId], new attempted value [${datasetId.get}]"
          case _                                           => datasetId
