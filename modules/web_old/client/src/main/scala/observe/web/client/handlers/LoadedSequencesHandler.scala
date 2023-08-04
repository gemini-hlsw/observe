// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import cats.syntax.all.*
import diode.ActionHandler
import diode.ActionResult
import diode.Effect
import diode.ModelRW
import diode.NoAction
import observe.model.SequenceState
import observe.model.events.*
import observe.web.client.actions.*
import observe.web.client.circuit.SODLocationFocus
import observe.web.client.model.ModelOps.*
import observe.web.client.model.Pages.*
import observe.web.client.services.ObserveWebClient

/**
 * Handles updates to the selected sequences set
 */
class LoadedSequencesHandler[M](modelRW: ModelRW[M, SODLocationFocus])
    extends ActionHandler(modelRW)
    with Handlers[M, SODLocationFocus] {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case ServerMessage(LoadSequenceUpdated(i, obsId, view, cid)) =>
      // Update selected and the page
      val upSelected    = if (value.clientId.exists(_ === cid)) {
        // if I requested the load also focus on it
        SODLocationFocus.sod.modify(
          _.updateFromQueue(view)
            .loadingComplete(obsId, i)
            .unsetPreviewOn(obsId)
            .focusOnSequence(i, obsId)
        )
      } else {
        SODLocationFocus.sod.modify(
          _.updateFromQueue(view).loadingComplete(obsId, i).unsetPreviewOn(obsId)
        )
      }
      val nextStepToRun =
        view.sessionQueue.find(_.obsId === obsId).flatMap(_.nextStepToRun)
      val upLocation    = Focus[SODLocationFocus](_.location).replace(
        SequencePage(i, obsId, StepIdDisplayed(nextStepToRun))
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

    case LoadSequence(observer, i, obsId) =>
      val loadSequence = value.clientId
        .map(cid =>
          Effect(
            ObserveWebClient
              .loadSequence(i, obsId, observer, cid)
              .as(NoAction)
          )
        )
        .getOrElse(VoidEffect)
      val update       = SODLocationFocus.sod.modify(_.markAsLoading(obsId))
      updatedLE(update, loadSequence)
  }
}
