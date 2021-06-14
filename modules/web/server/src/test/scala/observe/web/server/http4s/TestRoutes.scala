// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.effect.IO
import cats.tests.CatsSuite
import cats.effect.std.Queue
import fs2.concurrent.Topic
import giapi.client.GiapiStatusDb
import lucuma.core.enum.Site
import org.typelevel.log4cats.noop.NoOpLogger
import org.http4s._
import org.http4s.Uri.uri
import observe.server._
import observe.server.tcs.GuideConfigDb
import observe.model.events._
import observe.model.config._
import observe.web.server.http4s.encoder._
import observe.web.server.security.AuthenticationService
import observe.model.UserLoginRequest
import scala.concurrent.duration._
import cats.effect.Ref

trait TestRoutes extends ClientBooEncoders with CatsSuite {
  private implicit def logger = NoOpLogger.impl[IO]

  private val statusDb    = GiapiStatusDb.simulatedDb[IO]
  private val config      =
    AuthenticationConfig(FiniteDuration(8, HOURS), "token", "abc", useSSL = false, Nil)
  private val authService = AuthenticationService[IO](Mode.Development, config)

  def commandRoutes(engine: ObserveEngine[IO]) =
    for {
      q <- Queue.bounded[IO, executeEngine.EventType](10)
    } yield new ObserveCommandRoutes[IO](authService, q, engine).service

  val uiRoutes =
    for {
      o  <- Topic[IO, ObserveEvent]
      cs <- Ref.of[IO, ClientsSetDb.ClientsSet](Map.empty).map(ClientsSetDb.apply[IO](_))
    } yield new ObserveUIApiRoutes(Site.GS,
                                   Mode.Development,
                                   authService,
                                   GuideConfigDb.constant[IO],
                                   statusDb,
                                   cs,
                                   o
    ).service

  def newLoginToken: IO[String] =
    for {
      s <- uiRoutes
      r <- s
             .apply(
               Request(method = Method.POST, uri = uri("/observe/login"))
                 .withEntity(UserLoginRequest("telops", "pwd"))
             )
             .value
      k <- r.map(_.cookies).orEmpty.find(_.name === "token").pure[IO]
    } yield k.map(_.content).orEmpty
}
