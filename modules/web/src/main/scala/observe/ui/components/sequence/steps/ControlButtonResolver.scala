// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import observe.ui.model.enums.ClientMode
import observe.model.SequenceState

sealed trait ControlButtonResolver[A]:
  def extractor(a: A): (ClientMode, SequenceState, Boolean) // ExecutionStep)

  def controlButtonsActive(a: A): Boolean =
    val (clientMode, state, isRunningStep) = extractor(a)
    (clientMode.canOperate) && state.isRunning && ( /*step.isObserving || step.isObservePaused ||*/ isRunningStep || state.userStopRequested)

object ControlButtonResolver:
  def build[A](
    extractorFn: A => (ClientMode, SequenceState, Boolean)
  ): ControlButtonResolver[A] =
    new ControlButtonResolver[A]:
      override def extractor(a: A): (ClientMode, SequenceState, Boolean) = extractorFn(a)

extension [A](a: A)(using resolver: ControlButtonResolver[A])
  def controlButtonsActive: Boolean =
    resolver.controlButtonsActive(a)
