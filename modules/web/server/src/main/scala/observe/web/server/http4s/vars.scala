// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.syntax.all.*
import observe.model.ClientId

object ClientIDVar {
  def unapply(str: String): Option[ClientId] =
    Either.catchNonFatal(ClientId(java.util.UUID.fromString(str))).toOption
}
