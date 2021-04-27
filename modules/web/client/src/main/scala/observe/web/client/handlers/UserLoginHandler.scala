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
import diode.NoAction
import observe.common.HttpStatusCodes
import observe.model.UserDetails
import observe.web.client.actions._
import observe.web.client.services.ObserveWebClient

/**
 * Handles actions related to opening/closing the login box
 */
class UserLoginHandler[M](modelRW: ModelRW[M, Option[UserDetails]])
    extends ActionHandler(modelRW)
    with Handlers[M, Option[UserDetails]] {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case LoggedIn(u) =>
      // Close the login box
      val effect    = Effect(Future(CloseLoginBox))
      // Close the websocket and reconnect
      val reconnect = Effect(Future(Reconnect))
      updated(Some(u), reconnect + effect)

    case VerifyLoggedStatus =>
      val effect = Effect(ObserveWebClient.ping().map {
        case HttpStatusCodes.Unauthorized if value.isDefined => Logout
        case _                                               => NoAction
      })
      effectOnly(effect)

    case Logout =>
      val effect    = Effect(ObserveWebClient.logout().as(NoAction))
      val reConnect = Effect(Future(Reconnect))
      // Remove the user and call logout
      updated(None, effect + reConnect)
  }
}
