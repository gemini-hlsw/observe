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
import observe.model.SequenceState
import observe.model.events._
import observe.web.client.actions._
import observe.web.client.circuit.SODLocationFocus
import observe.web.client.model.ModelOps._
import observe.web.client.model.Pages._
import observe.web.client.services.ObserveWebClient

/**
 * Handles updates to the selected sequences set
 */
class LoadedSequencesHandler[M](modelRW: ModelRW[M, SODLocationFocus])
    extends ActionHandler(modelRW)
    with Handlers[M, SODLocationFocus] {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case ServerMessage(LoadSequenceUpdated(i, sid, view, cid)) =>
      // Update selected and the page
      val upSelected    = if (value.clientId.exists(_ === cid)) {
        // if I requested the load also focus on it
        SODLocationFocus.sod.modify(
          _.updateFromQueue(view)
            .loadingComplete(sid, i)
            .unsetPreviewOn(sid)
            .focusOnSequence(i, sid)
        )
      } else {
        SODLocationFocus.sod.modify(
          _.updateFromQueue(view).loadingComplete(sid, i).unsetPreviewOn(sid)
        )
      }
      val nextStepToRun =
        view.sessionQueue.find(_.id === sid).foldMap(_.nextStepToRun)
      val upLocation    = SODLocationFocus.location.set(
        SequencePage(i, sid, nextStepToRun.foldMap(StepIdDisplayed.apply))
      )
      updatedL(upSelected >>> upLocation)

    case ServerMessage(SequenceCompleted(sv)) =>
      // When a a sequence completes we keep the state client side
      sv.sessionQueue
        .find(_.status === SequenceState.Completed)
        .map { k =>
          updatedL(SODLocationFocus.sod.modify(_.markCompleted(k).updateFromQueue(sv)))
        }
        .getOrElse {
          noChange
        }

    case ServerMessage(s: ObserveModelUpdate) =>
      updatedL(SODLocationFocus.sod.modify(_.updateFromQueue(s.view)))

    case LoadSequence(observer, i, id) =>
      val loadSequence = value.clientId
        .map(cid =>
          Effect(
            ObserveWebClient
              .loadSequence(i, id, observer, cid)
              .as(NoAction)
          )
        )
        .getOrElse(VoidEffect)
      val update       = SODLocationFocus.sod.modify(_.markAsLoading(id))
      updatedLE(update, loadSequence)
  }
}
