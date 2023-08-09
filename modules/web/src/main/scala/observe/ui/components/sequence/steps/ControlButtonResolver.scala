// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import observe.model.ClientStatus
import observe.model.enums.SequenceState

sealed trait ControlButtonResolver[A]:
  def extractor(a: A): (ClientStatus, SequenceState, Boolean) // ExecutionStep)

  def controlButtonsActive(a: A): Boolean =
    val (clientStatus, state, isRunningStep) = extractor(a)
    clientStatus.isLogged && state.isRunning && ( /*step.isObserving || step.isObservePaused ||*/ isRunningStep || state.userStopRequested)

object ControlButtonResolver:
  def build[A](
    extractorFn: A => (ClientStatus, SequenceState, Boolean)
  ): ControlButtonResolver[A] =
    new ControlButtonResolver[A]:
      override def extractor(a: A): (ClientStatus, SequenceState, Boolean) = extractorFn(a)

extension [A](a: A)(using resolver: ControlButtonResolver[A])
  def controlButtonsActive: Boolean =
    resolver.controlButtonsActive(a)
