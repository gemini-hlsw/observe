// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.Applicative
import cats.effect.Sync
import cats.syntax.all.*
import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.enums.Instrument
import lucuma.core.enums.ObserveClass
import lucuma.core.enums.SequenceType
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Dataset
import lucuma.core.model.sequence.Step
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.TelescopeConfig as CoreTelescopeConfig
import observe.common.EventsGQL.*
import observe.model.dhs.*
import observe.model.odb.ObsRecordedIds

class DummyOdbCommands[F[_]: Sync] extends OdbCommands[F] {
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
    generatedId:     Option[Step.Id],
    generatedAtomId: Atom.Id,
    instrument:      Instrument,
    sequenceType:    SequenceType
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

  override def getCurrentRecordedIds: F[ObsRecordedIds] = ObsRecordedIds.Empty.pure[F]
}
