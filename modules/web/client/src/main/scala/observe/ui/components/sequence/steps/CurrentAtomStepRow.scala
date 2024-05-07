// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import lucuma.core.enums.Breakpoint
import lucuma.core.math.SignalToNoise
import lucuma.ui.sequence.SequenceRow
import observe.model.ObserveStep
import observe.model.StepState

case class CurrentAtomStepRow[+D](
  step:          ObserveStep,
  breakpoint:    Breakpoint,
  isFirstOfAtom: Boolean,
  signalToNoise: Option[SignalToNoise] = none // TODO Propagate S/N through the server
) extends SequenceRow[D]:
  val id                   = step.id.asRight
  val instrumentConfig     = step.instConfig.config.asInstanceOf[D].some
  val stepConfig           = step.stepConfig.some
  val isFinished           = step.status.isFinished
  // TODO This could be an estimate for pending steps, or the time it took for finished steps.
  // In either case, we don't have the information from the server.
  val stepEstimate         = none
  val stepState: StepState = step.status
