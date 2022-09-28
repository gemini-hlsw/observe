// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.effect.Sync
import cats.syntax.all._
import observe.web.server.security.{AuthenticationService, Http4sAuthentication}
import org.http4s._
import org.http4s.dsl._
import AuthenticationService.AuthResult

/**
 * Rest Endpoints to ping the backend and detect when you're logged out
 */
class PingRoutes[F[_]: Sync](auth: AuthenticationService[F]) extends Http4sDsl[F] {

  private val httpAuthentication               = new Http4sAuthentication(auth)
  val pingService: AuthedRoutes[AuthResult, F] =
    AuthedRoutes.of { case GET -> Root as user =>
      user.fold(_ => Response[F](Status.Unauthorized).pure[F], _ => Ok(""))
    }

  def service: HttpRoutes[F] = httpAuthentication.optAuth(pingService)

}
