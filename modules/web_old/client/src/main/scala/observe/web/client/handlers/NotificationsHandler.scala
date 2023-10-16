// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import cats.syntax.all.*
import diode.ActionHandler
import diode.ActionResult
import diode.Effect
import diode.ModelRW
import observe.model.Notification.*
import observe.model.events.UserNotification
import observe.web.client.actions.*
import observe.web.client.model.*

class NotificationsHandler[M](modelRW: ModelRW[M, UserNotificationState])
    extends ActionHandler(modelRW)
    with Handlers[M, UserNotificationState] {
  def handleUserNotification: PartialFunction[Any, ActionResult[M]] = {
    case ServerMessage(UserNotification(not, _)) =>
      // Update the notification state
      val lens         = Focus[UserNotificationState](_.notification).replace(not.some)
      // Request opening the dialog box
      val openBoxE     = Effect(Future(OpenUserNotificationBox))
      // Update the model as load failed
      val modelUpdateE = not match {
        case InstrumentInUse(obsId, _)  => Effect(Future(SequenceLoadFailed(obsId)))
        case ResourceConflict(obsId)    => Effect(Future(RunStartFailed(obsId)))
        case SubsystemBusy(obsId, _, r) => Effect(Future(ClearResourceOperations(obsId, r)))
        case RequestFailed(_)           => VoidEffect
      }
      updatedLE(lens, openBoxE >> modelUpdateE)
  }

  def handleCloseNotification: PartialFunction[Any, ActionResult[M]] = {
    case CloseUserNotificationBox =>
      updatedL(Focus[UserNotificationState](_.notification).replace(none))
  }

  def handleRequestFailedNotification: PartialFunction[Any, ActionResult[M]] = {
    case RequestFailedNotification(n) =>
      val openBoxE = Effect(Future(OpenUserNotificationBox))
      updatedLE(Focus[UserNotificationState](_.notification).replace(n.some), openBoxE)
  }

  def handle: PartialFunction[Any, ActionResult[M]] =
    List(handleUserNotification, handleRequestFailedNotification).combineAll
}
