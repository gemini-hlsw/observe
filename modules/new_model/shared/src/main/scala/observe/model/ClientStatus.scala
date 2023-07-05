// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.option.*

case class ClientStatus(
  user:         Option[UserDetails],
  clientId:     Option[ClientId],
  displayNames: Map[String, String]
) derives Eq:
  def isLogged: Boolean           = user.isDefined
  def canOperate: Boolean         = isLogged // && isConnected
  def displayName: Option[String] = user.flatMap(u => displayNames.get(u.username))
  def observer: Option[Observer]  = displayName.map(Observer(_))

object ClientStatus:
  val Default: ClientStatus = ClientStatus(none, none, Map.empty)
