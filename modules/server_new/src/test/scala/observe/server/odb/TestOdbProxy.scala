// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.Applicative
import cats.effect.Concurrent
import cats.effect.Ref
import cats.syntax.all.*
import eu.timepit.refined.types.numeric.NonNegShort
import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.Instrument
import lucuma.core.enums.ObservationWorkflowState
import lucuma.core.enums.ObserveClass
import lucuma.core.enums.SequenceType
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.ConstraintSet
import lucuma.core.model.ElevationRange
import lucuma.core.model.Observation
import lucuma.core.model.Program
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.ExecutionConfig
import lucuma.core.model.sequence.ExecutionSequence
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.model.sequence.Step
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.TelescopeConfig as CoreTelescopeConfig
import lucuma.core.model.sequence.gmos
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.StaticConfig
import lucuma.refined.*
import monocle.syntax.all.focus
import observe.common.ObsQueriesGQL.ObsQuery
import observe.common.ObsQueriesGQL.ObsQuery.Data
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation as ODBObservation
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment.GuideEnvironment
import observe.common.ObsQueriesGQL.RecordDatasetMutation.Data.RecordDataset.Dataset
import observe.model.dhs.ImageFileId
import observe.model.odb.ObsRecordedIds

trait TestOdbProxy[F[_]] extends OdbProxy[F] {
  def outCapture: F[List[TestOdbProxy.OdbEvent]]
}

object TestOdbProxy {

  case class State(
    sciences: List[Atom[DynamicConfig.GmosNorth]],
    out:      List[OdbEvent]
  )

  def build[F[_]: Concurrent](
    staticCfg:   Option[StaticConfig.GmosNorth] = None,
    acquisition: Option[Atom[DynamicConfig.GmosNorth]],
    sciences:    List[Atom[DynamicConfig.GmosNorth]] = List.empty
  ): F[TestOdbProxy[F]] = Ref
    .of[F, State](State(sciences, List.empty))
    .map(rf =>
      new TestOdbProxy[F] {
        private def addEvent(ev: OdbEvent): F[Unit] =
          rf.modify(s => (s.focus(_.out).modify(_.appended(ev)), ()))

        override def read(oid: Observation.Id): F[Data.Observation] = rf.get
          .map { st =>
            val sciAtom: Option[Atom[DynamicConfig.GmosNorth]] = st.sciences.headOption
            val sciTail: List[Atom[DynamicConfig.GmosNorth]]   = st.sciences match {
              case head :: tail => tail
              case Nil          => Nil
            }
            Data
              .Observation(
                oid,
                title = "Test Observation".refined,
                ODBObservation.Workflow(ObservationWorkflowState.Ready),
                Data.Observation.Program(Program.Id(PosLong.unsafeFrom(1))),
                Data.Observation.TargetEnvironment(none, GuideEnvironment(List.empty)),
                ConstraintSet(ImageQuality.TwoPointZero,
                              CloudExtinction.TwoPointZero,
                              SkyBackground.Bright,
                              WaterVapor.Wet,
                              ElevationRange.AirMass.Default
                ),
                List.empty,
                Data.Observation.Execution(
                  staticCfg.map(stc =>
                    InstrumentExecutionConfig.GmosNorth(
                      ExecutionConfig[StaticConfig.GmosNorth, DynamicConfig.GmosNorth](
                        stc,
                        acquisition.map(
                          ExecutionSequence[DynamicConfig.GmosNorth](_, List.empty, true)
                        ),
                        sciAtom.map(
                          ExecutionSequence[DynamicConfig.GmosNorth](
                            _,
                            sciTail,
                            sciTail.nonEmpty
                          )
                        )
                      )
                    )
                  )
                )
              )
          }

        override def visitStart(obsId: Observation.Id, staticCfg: StaticConfig): F[Unit] = addEvent(
          VisitStart(obsId, staticCfg)
        )

        override def sequenceStart(obsId: Observation.Id): F[Unit] = addEvent(SequenceStart(obsId))

        override def atomStart(
          obsId:        Observation.Id,
          instrument:   Instrument,
          sequenceType: SequenceType,
          stepCount:    NonNegShort,
          generatedId:  Option[Atom.Id]
        ): F[Unit] = (sequenceType match {
          case SequenceType.Acquisition => Applicative[F].unit
          case SequenceType.Science     =>
            rf.modify(s =>
              (s.focus(_.sciences)
                 .modify(ss =>
                   if (ss.isEmpty) List.empty
                   else ss.tail
                 ),
               ()
              )
            )
        }) *> addEvent(AtomStart(obsId, instrument, sequenceType, stepCount))

        override def stepStartStep(
          obsId:           Observation.Id,
          dynamicConfig:   DynamicConfig,
          stepConfig:      StepConfig,
          telescopeConfig: CoreTelescopeConfig,
          observeClass:    ObserveClass,
          generatedId:     Option[Step.Id]
        ): F[Unit] =
          addEvent(StepStartStep(obsId, dynamicConfig, stepConfig, telescopeConfig, observeClass))

        override def stepStartConfigure(obsId: Observation.Id): F[Unit] = addEvent(
          StepStartConfigure(obsId)
        )

        override def stepEndConfigure(obsId: Observation.Id): F[Boolean] =
          addEvent(StepEndConfigure(obsId)).as(true)

        override def stepStartObserve(obsId: Observation.Id): F[Boolean] =
          addEvent(StepStartObserve(obsId)).as(true)

        override def datasetStartExposure(obsId: Observation.Id, fileId: ImageFileId): F[Dataset] =
          addEvent(DatasetStartExposure(obsId, fileId)) *> Dataset(
            lucuma.core.model.sequence.Dataset
              .Id(PosLong.unsafeFrom(scala.util.Random.between(1L, Long.MaxValue))),
            None
          ).pure[F]

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

        override def stepAbort(obsId: Observation.Id): F[Boolean] =
          addEvent(StepAbort(obsId)).as(true)

        override def atomEnd(obsId: Observation.Id): F[Boolean] =
          addEvent(AtomEnd(obsId)).as(true)

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

        override def outCapture: F[List[OdbEvent]] = rf.get.map(_.out)

        override def getCurrentRecordedIds: F[ObsRecordedIds] = ObsRecordedIds.Empty.pure[F]
      }
    )

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
    obsId:           Observation.Id,
    dynamicConfig:   DynamicConfig,
    stepConfig:      StepConfig,
    telescopeConfig: CoreTelescopeConfig,
    observeClass:    ObserveClass
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
  case class StepAbort(obsId: Observation.Id)                                 extends OdbEvent
  case class AtomEnd(obsId: Observation.Id)                                   extends OdbEvent
  case class SequenceEnd(obsId: Observation.Id)                               extends OdbEvent
  case class ObsAbort(obsId: Observation.Id, reason: String)                  extends OdbEvent
  case class ObsContinue(obsId: Observation.Id)                               extends OdbEvent
  case class ObsPause(obsId: Observation.Id, reason: String)                  extends OdbEvent
  case class ObsStop(obsId: Observation.Id, reason: String)                   extends OdbEvent

}
