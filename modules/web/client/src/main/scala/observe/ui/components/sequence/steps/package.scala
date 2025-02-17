// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import lucuma.core.enums.SmartGcalType
import lucuma.core.model.sequence.StepConfig
import lucuma.core.util.TimeSpan
import lucuma.schemas.model.enums.StepExecutionState
import lucuma.ui.sequence.SequenceRow
import observe.model.ObserveStage
import observe.model.StepState
import observe.model.dhs.ImageFileId
import observe.model.enums.ExecutionStepType

def renderProgressLabel(
  fileId:            ImageFileId,
  remainingTimeSpan: Option[TimeSpan],
  isStopping:        Boolean,
  isPausedInStep:    Boolean,
  stage:             ObserveStage
): String =
  val durationStr: String = remainingTimeSpan
    .map(_.toMicroseconds)
    .filter(_ > 0)
    .foldMap: micros =>
      // s"mm:ss (s s) Remaining"
      val remainingSecs: Int = (micros / 1000000).toInt
      val remainingMins: Int = remainingSecs / 60
      val secsRemainder: Int = remainingSecs % 60
      val onlySecs: String   = if (remainingMins == 0) "" else s"($remainingSecs s)"
      List(f"$remainingMins:$secsRemainder%02d", onlySecs, "Remaining")
        .filterNot(_.isEmpty)
        .mkString(" ")

  val stageStr: String = (isPausedInStep, isStopping, stage) match
    case (true, _, _)                    => "Paused"
    case (_, true, _)                    => "Stopping - Reading out..."
    case (_, _, ObserveStage.Preparing)  => "Preparing"
    case (_, _, ObserveStage.ReadingOut) => "Reading out..."
    case _                               => ""

  // if (paused) s"$fileId - Paused$durationStr"
  // else if (stopping) s"$fileId - Stopping - Reading out..."
  // else
  //   stageStr match
  //     case Some(stage) => s"$fileId - $stage"
  //     case _           =>
  //       remainingMillis.fold(fileId.value): millis =>
  //         if (millis > 0) s"${fileId.value}$durationStr" else s"${fileId.value} - Reading out..."

  List(fileId.value, durationStr, stageStr)
    .filterNot(_.isEmpty)
    .mkString(" - ")

extension [D](row: SequenceRow[D])
  def stepType(isNodAndShuffle: Boolean): Option[ExecutionStepType] =
    (row.stepConfig, isNodAndShuffle) match
      case (Some(StepConfig.Bias), _)                          => ExecutionStepType.Bias.some
      case (Some(StepConfig.Dark), true)                       => ExecutionStepType.NodAndShuffleDark.some
      case (Some(StepConfig.Dark), _)                          => ExecutionStepType.Dark.some
      case (Some(StepConfig.Gcal(_, _, _, _)), _)              => ExecutionStepType.Calibration.some
      case (Some(StepConfig.Science), true)                    => ExecutionStepType.NodAndShuffle.some
      case (Some(StepConfig.Science), _)                       => ExecutionStepType.Object.some
      case (Some(StepConfig.SmartGcal(SmartGcalType.Arc)), _)  => ExecutionStepType.Arc.some
      case (Some(StepConfig.SmartGcal(SmartGcalType.Flat)), _) => ExecutionStepType.Flat.some
      case (Some(StepConfig.SmartGcal(_)), _)                  => none // Unknown SmartGcal type
      case _                                                   => none
      // TODO ExecutionStepType.AlignAndCalib in GPI

  def stepTime: StepTime =
    if (row.isFinished) StepTime.Past
    else
      row match
        case CurrentAtomStepRow(_, _, _, _) => StepTime.Present
        case _                              => StepTime.Future

  def isFirstInAtom: Boolean =
    row match
      case currentStep @ CurrentAtomStepRow(_, _, _, _)    => currentStep.isFirstOfAtom
      case futureStep @ SequenceRow.FutureStep(_, _, _, _) => futureStep.firstOf.isDefined
      case _                                               => false

  def stepState: StepState =
    row match
      case CurrentAtomStepRow(step, _, _, _)                => step.status
      case SequenceRow.FutureStep(_, _, _, _)               => StepState.Pending
      case SequenceRow.Executed.ExecutedStep(stepRecord, _) =>
        stepRecord.executionState match
          case StepExecutionState.NotStarted => StepState.Pending
          case StepExecutionState.Ongoing    => StepState.Running
          case StepExecutionState.Aborted    => StepState.Aborted
          case StepExecutionState.Completed  => StepState.Completed
          case StepExecutionState.Stopped    => StepState.Completed
          case StepExecutionState.Abandoned  => StepState.Failed("Abandoned")
      case _                                                => StepState.Completed
