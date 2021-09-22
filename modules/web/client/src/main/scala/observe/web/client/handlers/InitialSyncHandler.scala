// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import cats.syntax.all._
import diode.Action
import diode.ActionHandler
import diode.ActionResult
import diode.Effect
import diode.ModelRW
import observe.model.SequenceView
import observe.model.SequencesQueue
import observe.model.events.ObserveModelUpdate
import observe.web.client.actions._
import observe.web.client.circuit._
import observe.web.client.model.Pages._

/**
 * This handler is called only once. It will be triggered when the first message
 * with the full model arrives.
 * Then we sync to the first running sequence or to the route we are currently on
 */
class InitialSyncHandler[M](modelRW: ModelRW[M, InitialSyncFocus])
    extends ActionHandler(modelRW)
    with Handlers[M, InitialSyncFocus] {
  def runningSequence(s: ObserveModelUpdate): Option[SequenceView] =
    s.view.sessionQueue.find(_.status.isRunning)

  private def pageE(action: Action): InitialSyncFocus => InitialSyncFocus =
    PageActionP
      .getOption(action)
      .map(p => InitialSyncFocus.location.replace(p))
      .getOrElse(identity)

  private val noUpdate: InitialSyncFocus => InitialSyncFocus = identity

  def defaultPage(
    s: SequencesQueue[SequenceView]
  ): (InitialSyncFocus => InitialSyncFocus, Effect) = {
    val loaded = s.loaded.values.toList
    // An unkown page was shown
    val effect = loaded.headOption.flatMap { id =>
      s.sessionQueue.find(_.idName.id === id).map { s =>
        val nextStep = StepIdDisplayed(s.runningStep.flatMap(_.id))
        val action   = SelectIdToDisplay(s.metadata.instrument, id, nextStep)
        (pageE(action), Effect(Future(action)))
      }
    }
    effect.getOrElse((noUpdate, VoidEffect))
  }

  def handle: PartialFunction[Any, ActionResult[M]] = {
    // Otherwise, update the model to reflect the current page
    case ServerMessage(s: ObserveModelUpdate) if value.firstLoad =>
      // the page maybe not in sync with the tabs. Let's fix that
      val sids             = s.view.sessionQueue.map(_.idName)
      val loaded           = s.view.loaded.values.toList
      // update will change the url if needed and effect cat
      val (update, effect) = value.location match {
        case p @ SequencePage(_, id, _) if loaded.contains(id) =>
          // We need to effect to update the page
          (noUpdate, Effect(Future(PageActionP.reverseGet(p))))

        case SequencePage(i, id, s) if sids.contains(id) =>
          // If the page is on the list but not loaded go to preview
          val action = SelectSequencePreview(i, id, s)
          (pageE(action), Effect(Future(action)))

        case p @ SequenceConfigPage(_, id, _) if sids.contains(id) =>
          // We need to effect to update the reference
          (noUpdate, Effect(Future(PageActionP.reverseGet(p))))

        case p @ PreviewPage(i, id, st) if sids.contains(id) =>
          val isLoaded = loaded.contains(id)
          // We need to effect to update the reference
          if (isLoaded) {
            val action = SelectIdToDisplay(i, id, st)
            (pageE(action), Effect(Future(action)))
          } else {
            (noUpdate, Effect(Future(PageActionP.reverseGet(p))))
          }

        case PreviewConfigPage(i, id, st) if sids.contains(id) =>
          val isLoaded = loaded.contains(id)
          // We need to effect to update the reference
          if (isLoaded) {
            (noUpdate, Effect(Future(ShowStepConfig(i, id, st))))
          } else {
            (noUpdate, Effect(Future(ShowPreviewStepConfig(i, id, st))))
          }

        case Root | SequencePage(_, _, _) | PreviewPage(_, _, _) | SequenceConfigPage(_, _, _) |
            PreviewConfigPage(_, _, _) =>
          defaultPage(s.view)

        case _ =>
          // No matches
          (noUpdate, VoidEffect)
      }
      updatedLE(InitialSyncFocus.firstLoad.replace(false) >>> update,
                Effect(Future(CleanSequences)) >> effect
      )
  }
}
