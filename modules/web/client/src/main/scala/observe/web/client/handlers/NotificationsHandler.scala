// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import cats.syntax.all._
import diode.ActionHandler
import diode.ActionResult
import diode.Effect
import diode.ModelRW
import observe.model.Notification._
import observe.model.events.UserNotification
import observe.web.client.actions._
import observe.web.client.model._

class NotificationsHandler[M](modelRW: ModelRW[M, UserNotificationState])
    extends ActionHandler(modelRW)
    with Handlers[M, UserNotificationState] {
  def handleUserNotification: PartialFunction[Any, ActionResult[M]] = {
    case ServerMessage(UserNotification(not, _)) =>
      // Update the notification state
      val lens         = UserNotificationState.notification.replace(not.some)
      // Request opening the dialog box
      val openBoxE     = Effect(Future(OpenUserNotificationBox))
      // Update the model as load failed
      val modelUpdateE = not match {
        case InstrumentInUse(idName, _)  => Effect(Future(SequenceLoadFailed(idName)))
        case ResourceConflict(idName)    => Effect(Future(RunStartFailed(idName.id)))
        case SubsystemBusy(idName, _, r) => Effect(Future(ClearResourceOperations(idName.id, r)))
        case RequestFailed(_)            => VoidEffect
      }
      updatedLE(lens, openBoxE >> modelUpdateE)
  }

  def handleCloseNotification: PartialFunction[Any, ActionResult[M]] = {
    case CloseUserNotificationBox =>
      updatedL(UserNotificationState.notification.replace(none))
  }

  def handleRequestFailedNotification: PartialFunction[Any, ActionResult[M]] = {
    case RequestFailedNotification(n) =>
      val openBoxE = Effect(Future(OpenUserNotificationBox))
      updatedLE(UserNotificationState.notification.replace(n.some), openBoxE)
  }

  def handle: PartialFunction[Any, ActionResult[M]] =
    List(handleUserNotification, handleRequestFailedNotification).combineAll
}
