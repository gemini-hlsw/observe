// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import cats.syntax.all._
import diode.ActionHandler
import diode.ActionResult
import diode.Effect
import diode.ModelRW
import diode.NoAction
import observe.model.SequenceView
import observe.model.SequencesQueue
import observe.web.client.actions._
import observe.web.client.model.ModelOps._
import observe.web.client.services.ObserveWebClient

/**
 * Handles sequence execution actions
 */
class SequenceExecutionHandler[M](modelRW: ModelRW[M, SequencesQueue[SequenceView]])
    extends ActionHandler(modelRW)
    with Handlers[M, SequencesQueue[SequenceView]] {
  // def handleUpdateObserver: PartialFunction[Any, ActionResult[M]] = {
  //   case UpdateObserver(sequenceId, name) =>
  //     val updateObserverE  = Effect(
  //       SeqexecWebClient.setObserver(sequenceId, name.value).as(NoAction)
  //     ) + Effect.action(UpdateDefaultObserver(name))
  //     val updatedSequences =
  //       value.copy(sessionQueue = value.sessionQueue.collect {
  //         case s if s.id === sequenceId =>
  //           s.copy(metadata = s.metadata.copy(observer = Some(name)))
  //         case s                        => s
  //       })
  //     updated(updatedSequences, updateObserverE)
  // }

  def handleFlipSkipBreakpoint: PartialFunction[Any, ActionResult[M]] = {
    case FlipSkipStep(sequenceIdName, step) =>
      val skipRequest = Effect(ObserveWebClient.skip(sequenceIdName.id, step.flipSkip).as(NoAction))
      updated(value.copy(sessionQueue = value.sessionQueue.collect {
                case s if s.idName === sequenceIdName => s.flipSkipMarkAtStep(step)
                case s                                => s
              }),
              skipRequest
      )

    case FlipBreakpointStep(sequenceIdName, step) =>
      val breakpointRequest = Effect(
        ObserveWebClient
          .breakpoint(sequenceIdName.id, step.flipBreakpoint)
          .as(NoAction)
      )
      updated(value.copy(sessionQueue = value.sessionQueue.collect {
                case s if s.idName === sequenceIdName => s.flipBreakpointAtStep(step)
                case s                                => s
              }),
              breakpointRequest
      )
  }

  override def handle: PartialFunction[Any, ActionResult[M]] =
    List(handleFlipSkipBreakpoint).combineAll
}
