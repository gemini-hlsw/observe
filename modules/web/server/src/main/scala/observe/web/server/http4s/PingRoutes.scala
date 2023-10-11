// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.Monad
import fs2.compression.Compression
import lucuma.core.model.User
import lucuma.sso.client.SsoClient
import org.http4s.*
import org.http4s.dsl.*
import org.http4s.server.middleware.GZip

/**
 * Rest Endpoints to ping the backend and detect when you're logged out
 */
class PingRoutes[F[_]: Monad: Compression](ssoClient: SsoClient[F, User]) extends Http4sDsl[F] {

  val pingService: HttpRoutes[F] = HttpRoutes.of[F] { case req @ GET -> Root =>
    ssoClient.require(req) { _ =>
      Ok("")
    }
  }

  def service: HttpRoutes[F] =
    GZip(pingService)

}
