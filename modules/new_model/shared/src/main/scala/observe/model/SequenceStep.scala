// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import lucuma.schemas.model.StepRecord
import lucuma.core.model.sequence.Step
import cats.Eq
import cats.syntax.all.*
import cats.derived.*
import lucuma.core.math.Offset
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.StepConfig.Science
import lucuma.core.enums.GuideState
import lucuma.core.enums.Breakpoint
import lucuma.core.util.TimeSpan
import lucuma.core.model.sequence.StepEstimate
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.GmosGratingConfig
import lucuma.core.model.sequence.gmos.GmosFpuMask
import observe.model.enums.ExecutionStepType
import lucuma.core.model.sequence.StepConfig.Bias
import lucuma.core.enums.SmartGcalType

// TODO: Move to UI project? To lucuma-ui???
enum SequenceStep[D](
  val id:               Step.Id,
  stepConfig:           StepConfig,
  val instrumentConfig: D,
  val breakpoint:       Breakpoint,
  // val exposureTime:     Option[TimeSpan],
  val stepEstimate:     Option[StepEstimate],
  val isFinished:       Boolean
) derives Eq:
  case RecordedStep[D](step: StepRecord[D])
      extends SequenceStep[D](
        step.id,
        step.stepConfig,
        step.instrumentConfig,
        Breakpoint.Disabled,
        // step.duration,
        none,
        true
      )

  case FutureStep[D](step: Step[D])
      extends SequenceStep[D](
        step.id,
        step.stepConfig,
        step.instrumentConfig,
        step.breakpoint,
        // step.estimate.detector.map(_.focus.estimate),
        step.estimate.some,
        false
      )

  def science: Option[StepConfig.Science] = StepConfig.science.getOption(stepConfig)

  def offset: Option[Offset] = science.map(_.offset)

  def guiding: Option[GuideState] = science.map(_.guiding)

  def hasBreakpoint: Boolean = breakpoint === Breakpoint.Enabled

  def exposureTime: Option[TimeSpan] = instrumentConfig match
    case DynamicConfig.GmosNorth(exposure, _, _, _, _, _, _) => exposure.some
    case DynamicConfig.GmosSouth(exposure, _, _, _, _, _, _) => exposure.some
    case _                                                   => none

  def gratingName: Option[String] = instrumentConfig match
    case DynamicConfig.GmosNorth(_, _, _, _, Some(GmosGratingConfig.North(grating, _, _)), _, _) =>
      grating.shortName.some
    case DynamicConfig.GmosSouth(_, _, _, _, Some(GmosGratingConfig.South(grating, _, _)), _, _) =>
      grating.shortName.some
    case _                                                                                       =>
      none

  def filterName: Option[String] = instrumentConfig match
    case DynamicConfig.GmosNorth(_, _, _, _, _, Some(filter), _) => filter.shortName.some
    case DynamicConfig.GmosSouth(_, _, _, _, _, Some(filter), _) => filter.shortName.some
    case _                                                       => none

  def fpuName: Option[String] = instrumentConfig match
    case DynamicConfig.GmosNorth(_, _, _, _, _, _, Some(GmosFpuMask.Builtin(builtin)))     =>
      builtin.longName.some
    case DynamicConfig.GmosNorth(_, _, _, _, _, _, Some(GmosFpuMask.Custom(_, slitWidth))) =>
      slitWidth.longName.some
    case DynamicConfig.GmosSouth(_, _, _, _, _, _, Some(GmosFpuMask.Builtin(builtin)))     =>
      builtin.longName.some
    case DynamicConfig.GmosSouth(_, _, _, _, _, _, Some(GmosFpuMask.Custom(_, slitWidth))) =>
      slitWidth.longName.some
    case _                                                                                 =>
      none

  def stepType(isNodAndShuffle: Boolean): ExecutionStepType = (stepConfig, isNodAndShuffle) match
    case (StepConfig.Bias, _)                          => ExecutionStepType.Bias
    case (StepConfig.Dark, true)                       => ExecutionStepType.NodAndShuffleDark
    case (StepConfig.Dark, _)                          => ExecutionStepType.Dark
    case (StepConfig.Gcal(_, _, _, _), _)              => ExecutionStepType.Calibration
    case (StepConfig.Science(_, _), true)              => ExecutionStepType.NodAndShuffle
    case (StepConfig.Science(_, _), _)                 => ExecutionStepType.Object
    case (StepConfig.SmartGcal(SmartGcalType.Arc), _)  => ExecutionStepType.Arc
    case (StepConfig.SmartGcal(SmartGcalType.Flat), _) => ExecutionStepType.Flat
    case (StepConfig.SmartGcal(_), _)                  => ??? // Unknown SmartGcal type
    // TODO ExecutionStepType.AlignAndCalib in GPI

//   given Display[ExecutionStep] = Display.byShortName(s =>
//     s.status match {
//       case StepState.Pending                      => "Pending"
//       case StepState.Completed                    => "Done"
//       case StepState.Skipped                      => "Skipped"
//       case StepState.Failed(msg)                  => msg
//       case StepState.Running if s.isObserving     => "Observing..."
//       case StepState.Running if s.isObservePaused => "Exposure paused"
//       case StepState.Running if s.isConfiguring   => "Configuring..."
//       case StepState.Running                      => "Running..."
//       case StepState.Paused                       => "Paused"
//       case StepState.Aborted                      => "Aborted"
//     }
//   )

//   val observeStatus: Optional[ExecutionStep, ActionStatus] =
//     Optional[ExecutionStep, ActionStatus] {
//       case s: StandardStep      => s.observeStatus.some
//       case s: NodAndShuffleStep => s.nsStatus.observing.some
//     } { n => a =>
//       a match
//         case s: StandardStep      => StandardStep.observeStatus.replace(n)(s)
//         case s: NodAndShuffleStep =>
//           NodAndShuffleStep.nsStatus.andThen(NodAndShuffleStatus.observing).replace(n)(s)
//     }

//   val configStatus: Optional[ExecutionStep, List[(Resource, ActionStatus)]] =
//     Optional[ExecutionStep, List[(Resource, ActionStatus)]] {
//       case s: StandardStep      => s.configStatus.some
//       case s: NodAndShuffleStep => s.configStatus.some
//     } { n => a =>
//       a match
//         case s: StandardStep      => StandardStep.configStatus.replace(n)(s)
//         case s: NodAndShuffleStep => NodAndShuffleStep.configStatus.replace(n)(s)
//     }

//   extension (s: ExecutionStep)
//     def flipBreakpoint: ExecutionStep =
//       s match
//         case st: StandardStep      => StandardStep.breakpoint.negate(st)
//         case st: NodAndShuffleStep => NodAndShuffleStep.breakpoint.negate(st)

//     def flipSkip: ExecutionStep =
//       s match
//         case st: StandardStep      => StandardStep.skip.negate(st)
//         case st: NodAndShuffleStep => NodAndShuffleStep.skip.negate(st)

//     def file: Option[String] = None

//     def canSetBreakpoint(steps: List[ExecutionStep]): Boolean =
//       s.status.canSetBreakpoint && steps
//         .dropWhile(_.status.isFinished)
//         .drop(1)
//         .exists(_.id === s.id)

//     def canSetSkipmark: Boolean = s.status.canSetSkipmark

//     def hasError: Boolean = s.status.hasError

//     def isRunning: Boolean = s.status.isRunning

//     def runningOrComplete: Boolean = s.status.runningOrComplete

//     def isObserving: Boolean =
//       s match
//         case x: StandardStep      => x.observeStatus === ActionStatus.Running
//         case x: NodAndShuffleStep => x.nsStatus.observing === ActionStatus.Running

//     def isObservePaused: Boolean =
//       s match
//         case x: StandardStep      => x.observeStatus === ActionStatus.Paused
//         case x: NodAndShuffleStep => x.nsStatus.observing === ActionStatus.Paused

//     def isConfiguring: Boolean =
//       s match
//         case x: StandardStep      => x.configStatus.count(_._2 === ActionStatus.Running) > 0
//         case x: NodAndShuffleStep => x.configStatus.count(_._2 === ActionStatus.Running) > 0

//     def isFinished: Boolean = s.status.isFinished

//     def wasSkipped: Boolean = s.status.wasSkipped

//     def canConfigure: Boolean = s.status.canConfigure

//     def isMultiLevel: Boolean =
//       s match
//         case _: NodAndShuffleStep => true
//         case _                    => false

// object NodAndShuffleStep:
//   enum PendingObserveCmd derives Eq:
//     case PauseGracefully, StopGracefully
