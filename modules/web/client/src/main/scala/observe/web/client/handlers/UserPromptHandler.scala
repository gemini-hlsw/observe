// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import cats.syntax.all._
import diode.ActionHandler
import diode.ActionResult
import diode.Effect
import diode.ModelRW
import observe.model.UserPrompt
import observe.model.events.UserPromptNotification
import observe.web.client.actions._
import observe.web.client.model._

class UserPromptHandler[M](modelRW: ModelRW[M, UserPromptState])
    extends ActionHandler(modelRW)
    with Handlers[M, UserPromptState] {
  def handleUserNotification: PartialFunction[Any, ActionResult[M]] = {
    case ServerMessage(UserPromptNotification(not, _)) =>
      // Update the notification state
      val lens         = UserPromptState.notification.set(not.some)
      // Update the model as load failed
      val modelUpdateE = not match {
        case UserPrompt.ChecksOverride(idName, _, _, _) => Effect(Future(RunStartFailed(idName.id)))
      }
      updatedLE(lens, modelUpdateE)
  }

  def handleClosePrompt: PartialFunction[Any, ActionResult[M]] = { case CloseUserPromptBox(x) =>
    val overrideEffect = this.value.notification match {
      case Some(UserPrompt.ChecksOverride(id, stp, sidx, _)) if x === UserPromptResult.Cancel =>
        Effect(Future(RequestRunFrom(id, stp, sidx, RunOptions.ChecksOverride)))
      case _                                                                                  => VoidEffect
    }
    updatedLE(UserPromptState.notification.set(none), overrideEffect)
  }

  def handle: PartialFunction[Any, ActionResult[M]] =
    List(handleUserNotification, handleClosePrompt).combineAll
}
