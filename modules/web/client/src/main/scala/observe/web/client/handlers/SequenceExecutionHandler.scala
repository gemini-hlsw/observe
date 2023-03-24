// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import cats.syntax.all._
import diode.ActionHandler
import diode.ActionResult
import diode.Effect
import diode.ModelRW
import diode.NoAction
import observe.model.Observer
import observe.web.client.actions._
import observe.web.client.model.ModelOps._
import observe.web.client.services.ObserveWebClient
import observe.web.client.circuit.SequencesQueueFocus

/**
 * Handles sequence execution actions
 */
class SequenceExecutionHandler[M](modelRW: ModelRW[M, SequencesQueueFocus])
    extends ActionHandler(modelRW)
    with Handlers[M, SequencesQueueFocus] {

  def handleFlipSkipBreakpoint: PartialFunction[Any, ActionResult[M]] = {
    case FlipSkipStep(sequenceId, step) =>
      val skipRequest = Effect(
        ObserveWebClient
          .skip(sequenceId.id, Observer(value.displayName.orEmpty), step.flipSkip)
          .as(NoAction)
      )
      updatedLE(SequencesQueueFocus.sessionQueue.modify(_.collect {
                  case s if s.idName.id === sequenceId.id => s.flipSkipMarkAtStep(step)
                  case s                                  => s
                }),
                skipRequest
      )

    case FlipBreakpointStep(sequenceId, step) =>
      val breakpointRequest = Effect(
        ObserveWebClient
          .breakpoint(sequenceId.id, Observer(value.displayName.orEmpty), step.flipBreakpoint)
          .as(NoAction)
      )
      updatedLE(SequencesQueueFocus.sessionQueue.modify(_.collect {
                  case s if s.idName.id === sequenceId.id => s.flipBreakpointAtStep(step)
                  case s                                  => s
                }),
                breakpointRequest
      )
  }

  override def handle: PartialFunction[Any, ActionResult[M]] =
    List(handleFlipSkipBreakpoint).combineAll
}
