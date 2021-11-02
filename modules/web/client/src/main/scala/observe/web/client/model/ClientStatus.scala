// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import cats.Eq
import monocle.Getter
import monocle.Lens
import observe.model.UserDetails

/**
 * Utility class to let components more easily switch parts of the UI depending on the user and
 * connection state
 */
final case class ClientStatus(user: Option[UserDetails], w: WebSocketConnection) {
  def isLogged: Boolean    = user.isDefined
  def isConnected: Boolean = w.ws.isReady
  def canOperate: Boolean  = isLogged && isConnected
}

object ClientStatus {
  implicit val eq: Eq[ClientStatus] =
    Eq.by(x => (x.user, x.w))

  val clientStatusFocusL: Lens[ObserveAppRootModel, ClientStatus] =
    Lens[ObserveAppRootModel, ClientStatus](m => ClientStatus(m.uiModel.user, m.ws))(v =>
      m => m.copy(ws = v.w, uiModel = m.uiModel.copy(user = v.user))
    )

  val canOperateG: Getter[ObserveAppRootModel, Boolean] =
    clientStatusFocusL.andThen(Getter[ClientStatus, Boolean](_.canOperate))
}
