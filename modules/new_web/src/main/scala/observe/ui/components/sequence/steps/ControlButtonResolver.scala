// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import observe.model.ClientStatus
import observe.model.ExecutionStep
import observe.model.enums.SequenceState

sealed trait ControlButtonResolver[A]:
  def extractor(a: A): (ClientStatus, SequenceState, ExecutionStep)

  def controlButtonsActive(a: A): Boolean =
    val (clientStatus, state, step) = extractor(a)

    clientStatus.isLogged && state.isRunning && (step.isObserving || step.isObservePaused || state.userStopRequested)

object ControlButtonResolver:
  def build[A](
    extractorFn: A => (ClientStatus, SequenceState, ExecutionStep)
  ): ControlButtonResolver[A] =
    new ControlButtonResolver[A]:
      override def extractor(a: A): (ClientStatus, SequenceState, ExecutionStep) = extractorFn(a)

extension [A](a: A)(using resolver: ControlButtonResolver[A])
  def controlButtonsActive: Boolean =
    resolver.controlButtonsActive(a)
