// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.Applicative
import cats.effect.Ref
import cats.syntax.all.*
import eu.timepit.refined.types.numeric.NonNegShort
import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.Instrument
import lucuma.core.enums.ObsActiveStatus
import lucuma.core.enums.ObsStatus
import lucuma.core.enums.ObserveClass
import lucuma.core.enums.SequenceType
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.ConstraintSet
import lucuma.core.model.ElevationRange
import lucuma.core.model.Observation
import lucuma.core.model.Program
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.StaticConfig
import observe.common.ObsQueriesGQL.ObsQuery.Data
import observe.model.dhs.ImageFileId

object TestOdbProxy {

  def build[F[_]: Applicative](out: Ref[F, List[OdbEvent]]): OdbProxy[F] = new OdbProxy[F] {
    private def addEvent(ev: OdbEvent): F[Unit] = out.modify(x => (x.appended(ev), ()))

    override def read(oid: Observation.Id): F[Data.Observation] = Data
      .Observation(
        oid,
        "",
        ObsStatus.Ready,
        ObsActiveStatus.Active,
        Data.Observation.Program(Program.Id(PosLong.unsafeFrom(1))),
        Data.Observation.TargetEnvironment.apply(),
        ConstraintSet(ImageQuality.TwoPointZero,
                      CloudExtinction.TwoPointZero,
                      SkyBackground.Bright,
                      WaterVapor.Wet,
                      ElevationRange.AirMass.Default
        ),
        List.empty,
        Data.Observation.Execution.apply()
      )
      .pure[F]

    override def queuedSequences: F[List[Observation.Id]] = List.empty.pure[F]

    override def visitStart(obsId: Observation.Id, staticCfg: StaticConfig): F[Unit] = addEvent(
      VisitStart(obsId, staticCfg)
    )

    override def sequenceStart(obsId: Observation.Id): F[Unit] = addEvent(SequenceStart(obsId))

    override def atomStart(
      obsId:        Observation.Id,
      instrument:   Instrument,
      sequenceType: SequenceType,
      stepCount:    NonNegShort
    ): F[Unit] = addEvent(AtomStart(obsId, instrument, sequenceType, stepCount))

    override def stepStartStep(
      obsId:         Observation.Id,
      dynamicConfig: DynamicConfig,
      stepConfig:    StepConfig,
      observeClass:  ObserveClass
    ): F[Unit] = addEvent(StepStartStep(obsId, dynamicConfig, stepConfig, observeClass))

    override def stepStartConfigure(obsId: Observation.Id): F[Unit] = addEvent(
      StepStartConfigure(obsId)
    )

    override def stepEndConfigure(obsId: Observation.Id): F[Boolean] =
      addEvent(StepEndConfigure(obsId)).as(true)

    override def stepStartObserve(obsId: Observation.Id): F[Boolean] =
      addEvent(StepStartObserve(obsId)).as(true)

    override def datasetStartExposure(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      addEvent(DatasetStartExposure(obsId, fileId)).as(true)

    override def datasetEndExposure(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      addEvent(DatasetEndExposure(obsId, fileId)).as(true)

    override def datasetStartReadout(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      addEvent(DatasetStartReadout(obsId, fileId)).as(true)

    override def datasetEndReadout(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      addEvent(DatasetEndReadout(obsId, fileId)).as(true)

    override def datasetStartWrite(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      addEvent(DatasetStartWrite(obsId, fileId)).as(true)

    override def datasetEndWrite(obsId: Observation.Id, fileId: ImageFileId): F[Boolean] =
      addEvent(DatasetEndWrite(obsId, fileId)).as(true)

    override def stepEndObserve(obsId: Observation.Id): F[Boolean] =
      addEvent(StepEndObserve(obsId)).as(true)

    override def stepEndStep(obsId: Observation.Id): F[Boolean] =
      addEvent(StepEndStep(obsId)).as(true)

    override def sequenceEnd(obsId: Observation.Id): F[Boolean] =
      addEvent(SequenceEnd(obsId)).as(true)

    override def obsAbort(obsId: Observation.Id, reason: String): F[Boolean] =
      addEvent(ObsAbort(obsId, reason)).as(true)

    override def obsContinue(obsId: Observation.Id): F[Boolean] =
      addEvent(ObsContinue(obsId)).as(true)

    override def obsPause(obsId: Observation.Id, reason: String): F[Boolean] =
      addEvent(ObsPause(obsId, reason)).as(true)

    override def obsStop(obsId: Observation.Id, reason: String): F[Boolean] =
      addEvent(ObsStop(obsId, reason)).as(true)
  }

  sealed trait OdbEvent
  case class VisitStart(obsId: Observation.Id, staticCfg: StaticConfig)       extends OdbEvent
  case class SequenceStart(obsId: Observation.Id)                             extends OdbEvent
  case class AtomStart(
    obsId:        Observation.Id,
    instrument:   Instrument,
    sequenceType: SequenceType,
    stepCount:    NonNegShort
  ) extends OdbEvent
  case class StepStartStep(
    obsId:         Observation.Id,
    dynamicConfig: DynamicConfig,
    stepConfig:    StepConfig,
    observeClass:  ObserveClass
  ) extends OdbEvent
  case class StepStartConfigure(obsId: Observation.Id)                        extends OdbEvent
  case class StepEndConfigure(obsId: Observation.Id)                          extends OdbEvent
  case class StepStartObserve(obsId: Observation.Id)                          extends OdbEvent
  case class DatasetStartExposure(obsId: Observation.Id, fileId: ImageFileId) extends OdbEvent
  case class DatasetEndExposure(obsId: Observation.Id, fileId: ImageFileId)   extends OdbEvent
  case class DatasetStartReadout(obsId: Observation.Id, fileId: ImageFileId)  extends OdbEvent
  case class DatasetEndReadout(obsId: Observation.Id, fileId: ImageFileId)    extends OdbEvent
  case class DatasetStartWrite(obsId: Observation.Id, fileId: ImageFileId)    extends OdbEvent
  case class DatasetEndWrite(obsId: Observation.Id, fileId: ImageFileId)      extends OdbEvent
  case class StepEndObserve(obsId: Observation.Id)                            extends OdbEvent
  case class StepEndStep(obsId: Observation.Id)                               extends OdbEvent
  case class SequenceEnd(obsId: Observation.Id)                               extends OdbEvent
  case class ObsAbort(obsId: Observation.Id, reason: String)                  extends OdbEvent
  case class ObsContinue(obsId: Observation.Id)                               extends OdbEvent
  case class ObsPause(obsId: Observation.Id, reason: String)                  extends OdbEvent
  case class ObsStop(obsId: Observation.Id, reason: String)                   extends OdbEvent

}
