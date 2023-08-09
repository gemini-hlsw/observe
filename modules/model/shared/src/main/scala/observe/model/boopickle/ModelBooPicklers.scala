// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.boopickle

import java.time.*
import scala.collection.immutable.SortedSet
import boopickle.Pickler
import boopickle.CompositePickler
import boopickle.Default.UUIDPickler
import boopickle.Default.*
import cats.*
import cats.implicits.catsKernelOrderingForOrder
import cats.data.NonEmptySet
import cats.syntax.all.*
import eu.timepit.refined.api.RefType
import eu.timepit.refined.types.numeric.PosLong
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.enums.{
  CloudExtinction,
  GcalArc,
  GcalContinuum,
  GmosCustomSlitWidth,
  GmosGratingOrder,
  GmosNorthFpu,
  GmosNorthGrating,
  GmosSouthFpu,
  GmosSouthGrating,
  GuideState,
  ImageQuality,
  SkyBackground,
  SmartGcalType,
  WaterVapor
}
import lucuma.core.math.{Angle, Index, Offset, Wavelength}
import lucuma.core.util.TimeSpan
import lucuma.core.model.Program
import lucuma.core.util.Enumerated
import lucuma.core.model.sequence.gmos.{DynamicConfig, GmosCcdMode, GmosFpuMask, GmosGratingConfig}
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.StepConfig.Gcal.Lamp
import observe.model.GmosParameters.*
import observe.model.NodAndShuffleStep.PendingObserveCmd
import observe.model.Observation
import observe.model.UserPrompt.ChecksOverride
import observe.model.UserPrompt.SeqCheck
import observe.model.{*, given}
import observe.model.dhs.*
import observe.model.enums.*
import observe.model.events.*
import squants.time.Time
import squants.time.TimeConversions.*

import java.util.UUID

/**
 * Contains boopickle implicit picklers of model objects Boopickle can auto derive encoders but it
 * is preferred to make them explicitly
 */
trait ModelBooPicklers extends BooPicklerSyntax {
  given Pickler[Year]                                       = transformPickler(Year.of)(_.getValue)
  given Pickler[LocalDate]                                  =
    transformPickler(LocalDate.ofEpochDay)(_.toEpochDay)
  given programIdPickler: Pickler[Program.Id]               = generatePickler[Program.Id]
  given Pickler[Index]                                      = Index.fromShort.toPickler
  given Pickler[Observation.Id]                             = generatePickler[Observation.Id]
  given observationIdPickler: Pickler[List[Observation.Id]] =
    iterablePickler[Observation.Id, List]

  given Pickler[PosLong] =
    transformPickler[PosLong, Long]((l: Long) =>
      RefType
        .applyRef[PosLong](l)
        .getOrElse(throw new RuntimeException(s"Failed to decode value"))
    )(_.value)

  given Pickler[StepId] =
    transformPickler[StepId, UUID](lucuma.core.model.sequence.Step.Id.apply)(_.toUuid)

  given [T: Order: Pickler]: Pickler[NonEmptySet[T]] =
    transformPickler[NonEmptySet[T], (T, List[T])] { case (a, as) =>
      NonEmptySet(a, SortedSet.from(as))
    }(s => (s.head, s.tail.toList))

  def valuesMap[F[_]: Traverse, A, B](c: F[A], f: A => B): Map[B, A] =
    c.fproduct(f).map(_.swap).toList.toMap

  def sourceIndex[A: Enumerated]: Map[Int, A] =
    Enumerated[A].all.zipWithIndex.map(_.swap).toMap

  def valuesMapPickler[A: Enumerated, B: Monoid: Pickler](
    valuesMap: Map[B, A]
  ): Pickler[A] =
    transformPickler((t: B) =>
      valuesMap
        .get(t)
        .getOrElse(throw new RuntimeException(s"Failed to decode value"))
    )(t => valuesMap.find { case (_, v) => v === t }.foldMap(_._1))

  def enumeratedPickler[A: Enumerated]: Pickler[A] =
    valuesMapPickler[A, Int](sourceIndex[A])

  given Pickler[Time] =
    transformPickler((t: Double) => t.milliseconds)(_.toMilliseconds)

  given Pickler[Instrument] = enumeratedPickler[Instrument]
  given Pickler[Resource]   = enumeratedPickler[Resource]

  given Pickler[Operator]        = generatePickler[Operator]
  given Pickler[SystemOverrides] = generatePickler[SystemOverrides]

  given Pickler[SystemName] = enumeratedPickler[SystemName]

  given Pickler[Observer] = generatePickler[Observer]

  given Pickler[UserDetails] = generatePickler[UserDetails]

  given Pickler[Instant] =
    transformPickler((t: Long) => Instant.ofEpochMilli(t))(_.toEpochMilli)

  given Pickler[CloudExtinction] = enumeratedPickler[CloudExtinction]
  given Pickler[ImageQuality]    = enumeratedPickler[ImageQuality]
  given Pickler[SkyBackground]   = enumeratedPickler[SkyBackground]
  given Pickler[WaterVapor]      = enumeratedPickler[WaterVapor]
  given Pickler[Conditions]      = generatePickler[Conditions]

  given Pickler[SequenceState.Completed.type] =
    generatePickler[SequenceState.Completed.type]
  given Pickler[SequenceState.Idle.type]      =
    generatePickler[SequenceState.Idle.type]
  given Pickler[SequenceState.Running]        =
    generatePickler[SequenceState.Running]
  given Pickler[SequenceState.Failed]         =
    generatePickler[SequenceState.Failed]
  given Pickler[SequenceState.Aborted.type]   =
    generatePickler[SequenceState.Aborted.type]

  given CompositePickler[SequenceState] =
    compositePickler[SequenceState]
      .addConcreteType[SequenceState.Completed.type]
      .addConcreteType[SequenceState.Running]
      .addConcreteType[SequenceState.Failed]
      .addConcreteType[SequenceState.Aborted.type]
      .addConcreteType[SequenceState.Idle.type]

  given Pickler[ActionStatus] = enumeratedPickler[ActionStatus]

  given Pickler[StepState.Pending.type]                              =
    generatePickler[StepState.Pending.type]
  given stepStateCompletedPickler: Pickler[StepState.Completed.type] =
    generatePickler[StepState.Completed.type]
  given Pickler[StepState.Skipped.type]                              =
    generatePickler[StepState.Skipped.type]
  given stepStateFailedPickler: Pickler[StepState.Failed]            = generatePickler[StepState.Failed]
  given Pickler[StepState.Running.type]                              =
    generatePickler[StepState.Running.type]
  given Pickler[StepState.Paused.type]                               =
    generatePickler[StepState.Paused.type]
  given stepStateAbortedPickler: Pickler[StepState.Aborted.type]     =
    generatePickler[StepState.Aborted.type]

  given CompositePickler[StepState] = compositePickler[StepState]
    .addConcreteType[StepState.Pending.type]
    .addConcreteType[StepState.Completed.type]
    .addConcreteType[StepState.Skipped.type]
    .addConcreteType[StepState.Failed]
    .addConcreteType[StepState.Aborted.type]
    .addConcreteType[StepState.Running.type]
    .addConcreteType[StepState.Paused.type]

  given imageIdPickler: Pickler[ImageFileId] =
    transformPickler(ImageFileId.apply(_))(_.value)

  given Pickler[TimeSpan]                             =
    transformPickler[TimeSpan, Long](TimeSpan.unsafeFromMicroseconds)(_.toMicroseconds)
  given Pickler[GmosCcdMode]                          = generatePickler[GmosCcdMode]
  given Pickler[GmosNorthGrating]                     = enumeratedPickler[GmosNorthGrating]
  given Pickler[GmosSouthGrating]                     = enumeratedPickler[GmosSouthGrating]
  given Pickler[GmosGratingOrder]                     = enumeratedPickler[GmosGratingOrder]
  given Pickler[Wavelength]                           = transformPickler[Wavelength, Int](Wavelength.unsafeFromIntPicometers)(
    Wavelength.intPicometers.reverseGet
  )
  given Pickler[GmosGratingConfig.North]              = generatePickler[GmosGratingConfig.North]
  given Pickler[GmosGratingConfig.South]              = generatePickler[GmosGratingConfig.South]
  given Pickler[GmosNorthFpu]                         = enumeratedPickler[GmosNorthFpu]
  given Pickler[GmosSouthFpu]                         = enumeratedPickler[GmosSouthFpu]
  given Pickler[NonEmptyString]                       =
    transformPickler[NonEmptyString, String](NonEmptyString.unsafeFrom)(_.toString)
  given Pickler[GmosCustomSlitWidth]                  = enumeratedPickler[GmosCustomSlitWidth]
  given [T: Pickler]: Pickler[GmosFpuMask.Builtin[T]] = generatePickler[GmosFpuMask.Builtin[T]]
  given Pickler[GmosFpuMask.Custom]                   = generatePickler[GmosFpuMask.Custom]
  given [T: Pickler]: Pickler[GmosFpuMask[T]]         = compositePickler[GmosFpuMask[T]]
    .addConcreteType[GmosFpuMask.Custom]
    .addConcreteType[GmosFpuMask.Builtin[T]]
  given Pickler[DynamicConfig.GmosNorth]              = generatePickler[DynamicConfig.GmosNorth]
  given Pickler[DynamicConfig.GmosSouth]              = generatePickler[DynamicConfig.GmosSouth]
  given Pickler[DynamicConfig]                        = compositePickler[DynamicConfig]
    .addConcreteType[DynamicConfig.GmosNorth]
    .addConcreteType[DynamicConfig.GmosSouth]

  given Pickler[StepConfig.Bias.type]     = generatePickler[StepConfig.Bias.type]
  given Pickler[StepConfig.Dark.type]     = generatePickler[StepConfig.Dark.type]
  given Pickler[GcalContinuum]            = enumeratedPickler[GcalContinuum]
  given Pickler[GcalArc]                  = enumeratedPickler[GcalArc]
  given Pickler[Lamp]                     = {
    import Lamp.*
    transformPickler[Lamp, Either[GcalContinuum, NonEmptySet[GcalArc]]](Lamp.fromEither)(_.toEither)
  }
  given Pickler[StepConfig.Gcal]          = generatePickler[StepConfig.Gcal]
  given Pickler[SmartGcalType]            = enumeratedPickler[SmartGcalType]
  given Pickler[StepConfig.SmartGcal]     = generatePickler[StepConfig.SmartGcal]
  given Pickler[Angle]                    =
    transformPickler[Angle, Long](Angle.fromMicroarcseconds)(_.toMicroarcseconds)
  given [A]: Pickler[Offset.Component[A]] =
    transformPickler[Offset.Component[A], Angle](Offset.Component.apply(_))(_.toAngle)
  given Pickler[Offset]                   = generatePickler[Offset]
  given Pickler[GuideState]               = enumeratedPickler[GuideState]
  given Pickler[StepConfig.Science]       = generatePickler[StepConfig.Science]

  given Pickler[StepConfig] = compositePickler[StepConfig]
    .addConcreteType[StepConfig.Bias.type]
    .addConcreteType[StepConfig.Dark.type]
    .addConcreteType[StepConfig.Gcal]
    .addConcreteType[StepConfig.SmartGcal]
    .addConcreteType[StepConfig.Science]

  given Pickler[StandardStep]        = generatePickler[StandardStep]
  given Pickler[NodAndShuffleStage]  = enumeratedPickler[NodAndShuffleStage]
  given Pickler[NSAction]            = enumeratedPickler[NSAction]
  given Pickler[NsCycles]            = transformPickler(NsCycles.apply(_))(_.value)
  given Pickler[NSSubexposure]       =
    transformPickler[NSSubexposure, (NsCycles, NsCycles, Int)] {
      case (t: NsCycles, c: NsCycles, i: Int) =>
        NSSubexposure
          .apply(t, c, i)
          .getOrElse(
            throw new RuntimeException("Failed to decode ns subexposure")
          )
      case null                               => throw new RuntimeException("Failed to decode ns subexposure")
    }((ns: NSSubexposure) => (ns.totalCycles, ns.cycle, ns.stageIndex))
  given Pickler[NSRunningState]      = generatePickler[NSRunningState]
  given Pickler[NodAndShuffleStatus] = generatePickler[NodAndShuffleStatus]
  given Pickler[PendingObserveCmd]   =
    enumeratedPickler[PendingObserveCmd]
  given Pickler[NodAndShuffleStep]   = generatePickler[NodAndShuffleStep]

  given CompositePickler[Step] = compositePickler[Step]
    .addConcreteType[StandardStep]
    .addConcreteType[NodAndShuffleStep]

  given Pickler[SequenceMetadata] =
    generatePickler[SequenceMetadata]

  given Pickler[SequenceView] = generatePickler[SequenceView]
  given Pickler[ClientId]     = generatePickler[ClientId]

  given Pickler[QueueId]                         = generatePickler[QueueId]
  given Pickler[QueueManipulationOp.Moved]       =
    generatePickler[QueueManipulationOp.Moved]
  given Pickler[QueueManipulationOp.Started]     =
    generatePickler[QueueManipulationOp.Started]
  given Pickler[QueueManipulationOp.Stopped]     =
    generatePickler[QueueManipulationOp.Stopped]
  given Pickler[QueueManipulationOp.Clear]       =
    generatePickler[QueueManipulationOp.Clear]
  given Pickler[QueueManipulationOp.AddedSeqs]   =
    generatePickler[QueueManipulationOp.AddedSeqs]
  given Pickler[QueueManipulationOp.RemovedSeqs] =
    generatePickler[QueueManipulationOp.RemovedSeqs]

  given CompositePickler[QueueManipulationOp] =
    compositePickler[QueueManipulationOp]
      .addConcreteType[QueueManipulationOp.Clear]
      .addConcreteType[QueueManipulationOp.Started]
      .addConcreteType[QueueManipulationOp.Stopped]
      .addConcreteType[QueueManipulationOp.Moved]
      .addConcreteType[QueueManipulationOp.AddedSeqs]
      .addConcreteType[QueueManipulationOp.RemovedSeqs]

  given singleActionOpStartedPickler: Pickler[SingleActionOp.Started] =
    generatePickler[SingleActionOp.Started]
  given Pickler[SingleActionOp.Completed]                             =
    generatePickler[SingleActionOp.Completed]
  given Pickler[SingleActionOp.Error]                                 =
    generatePickler[SingleActionOp.Error]
  given CompositePickler[SingleActionOp]                              =
    compositePickler[SingleActionOp]
      .addConcreteType[SingleActionOp.Started]
      .addConcreteType[SingleActionOp.Completed]
      .addConcreteType[SingleActionOp.Error]

  given batchCommandStateIdlePickler: Pickler[BatchCommandState.Idle.type] =
    generatePickler[BatchCommandState.Idle.type]
  given Pickler[BatchCommandState.Run]                                     =
    generatePickler[BatchCommandState.Run]
  given Pickler[BatchCommandState.Stop.type]                               =
    generatePickler[BatchCommandState.Stop.type]

  given CompositePickler[BatchCommandState] =
    compositePickler[BatchCommandState]
      .addConcreteType[BatchCommandState.Idle.type]
      .addConcreteType[BatchCommandState.Run]
      .addConcreteType[BatchCommandState.Stop.type]

  given batchExecStatePickler: Pickler[BatchExecState] = enumeratedPickler[BatchExecState]

  given Pickler[ExecutionQueueView] =
    generatePickler[ExecutionQueueView]

  given sequenceQueueIdPickler: Pickler[SequencesQueue[Observation.Id]] =
    generatePickler[SequencesQueue[Observation.Id]]

  given sequenceQueueViewPickler: Pickler[SequencesQueue[SequenceView]] =
    generatePickler[SequencesQueue[SequenceView]]

  given Pickler[ComaOption] = enumeratedPickler[ComaOption]

  given Pickler[TipTiltSource]  = enumeratedPickler[TipTiltSource]
  given Pickler[ServerLogLevel] = enumeratedPickler[ServerLogLevel]
  given Pickler[M1Source]       = enumeratedPickler[M1Source]

  given Pickler[MountGuideOption]              = enumeratedPickler[MountGuideOption]
  given Pickler[M1GuideConfig.M1GuideOn]       =
    generatePickler[M1GuideConfig.M1GuideOn]
  given Pickler[M1GuideConfig.M1GuideOff.type] =
    generatePickler[M1GuideConfig.M1GuideOff.type]
  given CompositePickler[M1GuideConfig]        =
    compositePickler[M1GuideConfig]
      .addConcreteType[M1GuideConfig.M1GuideOn]
      .addConcreteType[M1GuideConfig.M1GuideOff.type]

  given Pickler[M2GuideConfig.M2GuideOn]       =
    generatePickler[M2GuideConfig.M2GuideOn]
  given Pickler[M2GuideConfig.M2GuideOff.type] =
    generatePickler[M2GuideConfig.M2GuideOff.type]
  given CompositePickler[M2GuideConfig]        =
    compositePickler[M2GuideConfig]
      .addConcreteType[M2GuideConfig.M2GuideOn]
      .addConcreteType[M2GuideConfig.M2GuideOff.type]

  given Pickler[TelescopeGuideConfig] =
    generatePickler[TelescopeGuideConfig]

  given Pickler[Notification.ResourceConflict] =
    generatePickler[Notification.ResourceConflict]
  given Pickler[Notification.InstrumentInUse]  =
    generatePickler[Notification.InstrumentInUse]
  given Pickler[Notification.RequestFailed]    =
    generatePickler[Notification.RequestFailed]
  given Pickler[Notification.SubsystemBusy]    =
    generatePickler[Notification.SubsystemBusy]
  given Pickler[Notification]                  =
    compositePickler[Notification]
      .addConcreteType[Notification.ResourceConflict]
      .addConcreteType[Notification.InstrumentInUse]
      .addConcreteType[Notification.RequestFailed]
      .addConcreteType[Notification.SubsystemBusy]

  given Pickler[UserPrompt.ObsConditionsCheckOverride] =
    generatePickler[UserPrompt.ObsConditionsCheckOverride]
  given Pickler[UserPrompt.TargetCheckOverride]        =
    generatePickler[UserPrompt.TargetCheckOverride]
  given CompositePickler[SeqCheck]                     =
    compositePickler[SeqCheck]
      .addConcreteType[UserPrompt.TargetCheckOverride]
      .addConcreteType[UserPrompt.ObsConditionsCheckOverride]
  given Pickler[ChecksOverride]                        =
    generatePickler[UserPrompt.ChecksOverride]
  given Pickler[UserPrompt]                            =
    compositePickler[UserPrompt]
      .addConcreteType[ChecksOverride]

  given Pickler[ConnectionOpenEvent]         =
    generatePickler[ConnectionOpenEvent]
  given Pickler[SequenceStart]               = generatePickler[SequenceStart]
  given Pickler[StepExecuted]                = generatePickler[StepExecuted]
  given Pickler[FileIdStepExecuted]          =
    generatePickler[FileIdStepExecuted]
  given Pickler[SequenceCompleted]           =
    generatePickler[SequenceCompleted]
  given Pickler[SequenceLoaded]              = generatePickler[SequenceLoaded]
  given Pickler[SequenceUnloaded]            =
    generatePickler[SequenceUnloaded]
  given Pickler[StepBreakpointChanged]       =
    generatePickler[StepBreakpointChanged]
  given Pickler[OperatorUpdated]             = generatePickler[OperatorUpdated]
  given Pickler[ObserverUpdated]             = generatePickler[ObserverUpdated]
  given Pickler[ConditionsUpdated]           =
    generatePickler[ConditionsUpdated]
  given Pickler[LoadSequenceUpdated]         =
    generatePickler[LoadSequenceUpdated]
  given Pickler[ClearLoadedSequencesUpdated] =
    generatePickler[ClearLoadedSequencesUpdated]
  given Pickler[StepSkipMarkChanged]         =
    generatePickler[StepSkipMarkChanged]
  given Pickler[SequencePauseRequested]      =
    generatePickler[SequencePauseRequested]
  given Pickler[SequencePauseCanceled]       =
    generatePickler[SequencePauseCanceled]
  given Pickler[SequenceRefreshed]           =
    generatePickler[SequenceRefreshed]
  given Pickler[ActionStopRequested]         =
    generatePickler[ActionStopRequested]
  given Pickler[SequenceStopped]             = generatePickler[SequenceStopped]
  given Pickler[SequenceAborted]             = generatePickler[SequenceAborted]
  given Pickler[SequenceUpdated]             = generatePickler[SequenceUpdated]
  given Pickler[SequenceError]               = generatePickler[SequenceError]
  given Pickler[SequencePaused]              = generatePickler[SequencePaused]
  given Pickler[ExposurePaused]              = generatePickler[ExposurePaused]
  given Pickler[ServerLogMessage]            =
    generatePickler[ServerLogMessage]
  given Pickler[UserNotification]            =
    generatePickler[UserNotification]
  given Pickler[UserPromptNotification]      =
    generatePickler[UserPromptNotification]
  given Pickler[GuideConfigUpdate]           = generatePickler[GuideConfigUpdate]
  given Pickler[QueueUpdated]                = generatePickler[QueueUpdated]
  given Pickler[ObserveStage]                = enumeratedPickler[ObserveStage]
  given Pickler[ObservationProgress]         =
    generatePickler[ObservationProgress]
  given Pickler[NSObservationProgress]       =
    generatePickler[NSObservationProgress]
  given CompositePickler[Progress]           = compositePickler[Progress]
    .addConcreteType[ObservationProgress]
    .addConcreteType[NSObservationProgress]
  given Pickler[ObservationProgressEvent]    =
    generatePickler[ObservationProgressEvent]
  given Pickler[AlignAndCalibEvent]          = generatePickler[AlignAndCalibEvent]
  given Pickler[SingleActionEvent]           =
    generatePickler[SingleActionEvent]
  given Pickler[events.NullEvent.type]       = generatePickler[NullEvent.type]
  given Pickler[OverridesUpdated]            =
    generatePickler[OverridesUpdated]

  // Composite pickler for the observe event hierarchy
  given CompositePickler[ObserveEvent] = compositePickler[ObserveEvent]
    .addConcreteType[ConnectionOpenEvent]
    .addConcreteType[SequenceStart]
    .addConcreteType[StepExecuted]
    .addConcreteType[FileIdStepExecuted]
    .addConcreteType[SequenceCompleted]
    .addConcreteType[SequenceLoaded]
    .addConcreteType[SequenceUnloaded]
    .addConcreteType[StepBreakpointChanged]
    .addConcreteType[OperatorUpdated]
    .addConcreteType[ObserverUpdated]
    .addConcreteType[ConditionsUpdated]
    .addConcreteType[LoadSequenceUpdated]
    .addConcreteType[ClearLoadedSequencesUpdated]
    .addConcreteType[StepSkipMarkChanged]
    .addConcreteType[SequencePauseRequested]
    .addConcreteType[SequencePauseCanceled]
    .addConcreteType[SequenceRefreshed]
    .addConcreteType[ActionStopRequested]
    .addConcreteType[SequenceStopped]
    .addConcreteType[SequenceAborted]
    .addConcreteType[SequenceUpdated]
    .addConcreteType[SequenceError]
    .addConcreteType[SequencePaused]
    .addConcreteType[ExposurePaused]
    .addConcreteType[ServerLogMessage]
    .addConcreteType[UserNotification]
    .addConcreteType[UserPromptNotification]
    .addConcreteType[GuideConfigUpdate]
    .addConcreteType[QueueUpdated]
    .addConcreteType[ObservationProgressEvent]
    .addConcreteType[SingleActionEvent]
    .addConcreteType[AlignAndCalibEvent]
    .addConcreteType[OverridesUpdated]
    .addConcreteType[NullEvent.type]

  given Pickler[UserLoginRequest] = generatePickler[UserLoginRequest]

}
