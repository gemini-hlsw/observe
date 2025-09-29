// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.Applicative
import cats.MonadThrow
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.Instrument
import lucuma.core.enums.ObserveClass
import lucuma.core.enums.SequenceType
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Step
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.TelescopeConfig as CoreTelescopeConfig
import observe.model.dhs.*
import observe.model.odb.ObsRecordedIds
import observe.server.ObserveFailure

class DummyOdbProxy[F[_]: Sync] extends OdbProxy[F] {
  val evCmds = new DummyOdbCommands[F]

  override def read(oid: Observation.Id): F[OdbObservationData] =
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
    generatedId:     Option[Step.Id],
    generatedAtomId: Atom.Id,
    instrument:      Instrument,
    sequenceType:    SequenceType
  ): F[Unit] = Applicative[F].unit

  override def stepStartConfigure(obsId: Observation.Id): F[Unit] = Applicative[F].unit

  override def stepEndConfigure(obsId: Observation.Id): F[Boolean] = false.pure[F]

  override def stepStartObserve(obsId: Observation.Id): F[Boolean] = false.pure[F]

  override def stepEndObserve(obsId: Observation.Id): F[Boolean] = false.pure[F]

  override def stepEndStep(obsId: Observation.Id): F[Boolean] = false.pure[F]

  override def stepAbort(obsId: Observation.Id): F[Boolean] = false.pure[F]

  override def visitStart[S](obsId: Observation.Id, staticCfg: S): F[Unit] =
    Applicative[F].unit

  override def getCurrentRecordedIds: F[ObsRecordedIds] = ObsRecordedIds.Empty.pure[F]
}
