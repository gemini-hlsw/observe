// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.MonadThrow
import cats.effect.Ref
import cats.syntax.all.*
import lucuma.core.model.Observation
import lucuma.core.model.Visit
import lucuma.core.model.sequence.Dataset
import observe.server.ObserveFailure

trait IdTrackerOps[F[_]: MonadThrow](idTracker: Ref[F, ObsRecordedIds]):
  protected def getCurrentVisitId(obsId: Observation.Id): F[Visit.Id] =
    idTracker.get
      .map:
        ObsRecordedIds
          .at(obsId)
          .get(_)
          .map(RecordedVisit.visitId.get)
          .toRight(ObserveFailure.Unexpected(s"No current recorded visit for obsId [$obsId]"))
      .rethrow

  protected def setCurrentVisitId(obsId: Observation.Id, visitId: Option[Visit.Id]): F[Unit] =
    idTracker.update:
      ObsRecordedIds.at(obsId).replace(visitId.map(RecordedVisit(_)))

  protected def getCurrentAtomId(obsId: Observation.Id): F[RecordedAtomId] =
    idTracker.get
      .map:
        ObsRecordedIds
          .at(obsId)
          .get(_)
          .flatMap(RecordedVisit.atomId.getOption)
          .toRight(ObserveFailure.Unexpected(s"No current recorded atom for obsId [$obsId]"))
      .rethrow

  protected def setCurrentAtomId(obsId: Observation.Id, atomId: RecordedAtomId): F[Unit] =
    idTracker.update:
      ObsRecordedIds.at(obsId).some.andThen(RecordedVisit.atom).replace(RecordedAtom(atomId).some)

  protected def getCurrentStepId(obsId: Observation.Id): F[RecordedStepId] =
    idTracker.get
      .map:
        ObsRecordedIds
          .at(obsId)
          .get(_)
          .flatMap(RecordedVisit.stepId.getOption)
          .toRight(ObserveFailure.Unexpected(s"No current recorded step for obsId [$obsId]"))
      .rethrow

  protected def setCurrentStepId(obsId: Observation.Id, stepId: Option[RecordedStepId]): F[Unit] =
    idTracker.update:
      ObsRecordedIds.at(obsId).some.andThen(RecordedVisit.step).replace(stepId.map(RecordedStep(_)))

  protected def getCurrentDatasetId(obsId: Observation.Id): F[Dataset.Id] =
    idTracker.get
      .map:
        ObsRecordedIds
          .at(obsId)
          .get(_)
          .flatMap(RecordedVisit.datasetId.getOption)
          .flatten
          .toRight(ObserveFailure.Unexpected(s"No current recorded dataset for obsId [$obsId]"))
      .rethrow

  protected def setCurrentDatasetId(obsId: Observation.Id, datasetId: Option[Dataset.Id]): F[Unit] =
    idTracker.update:
      ObsRecordedIds.at(obsId).some.andThen(RecordedVisit.datasetId).replace(datasetId)
