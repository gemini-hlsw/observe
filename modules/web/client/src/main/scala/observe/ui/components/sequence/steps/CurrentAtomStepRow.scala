// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import lucuma.core.enums.Breakpoint
import lucuma.core.math.SignalToNoise
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.ui.sequence.SequenceRow
import observe.model.ObserveStep
import observe.model.StepState

case class CurrentAtomStepRow(
  step:          ObserveStep,
  breakpoint:    Breakpoint,
  isFirstOfAtom: Boolean,
  signalToNoise: Option[SignalToNoise] = none // TODO Propagate this information through the server
) extends SequenceRow[DynamicConfig]:
  val id                   = step.id.asRight
  val instrumentConfig     = step.instConfig.some
  val stepConfig           = step.stepConfig.some
  val isFinished           = step.status.isFinished
  // TODO This could be an estimate for pending steps, or the time it took for finished steps.
  // In either case, we don't have the information from the server.
  val stepEstimate         = none
  val stepState: StepState = step.status
