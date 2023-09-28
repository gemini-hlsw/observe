// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.effect.IO
import cats.effect.std.Queue
import fs2.concurrent.Topic
import giapi.client.GiapiStatusDb
import lucuma.core.enums.Site
import org.typelevel.log4cats.noop.NoOpLogger
import org.http4s.*
import org.http4s.implicits.*
import observe.server.{*, given}
import org.typelevel.log4cats.Logger
//import observe.server.tcs.GuideConfigDb
import observe.model.events.*
import observe.model.config.*
// import observe.web.server.security.AuthenticationService
import observe.model.UserLoginRequest
import scala.concurrent.duration.*
import cats.effect.Ref
import org.http4s.server.websocket.WebSocketBuilder2

trait TestRoutes {
  given logger: Logger[IO] = NoOpLogger.impl[IO]

  private val statusDb = GiapiStatusDb.simulatedDb[IO]
  // private val config      =
  //   AuthenticationConfig(FiniteDuration(8, HOURS), "token", "abc", useSsl = false, Nil)
  // private val authService = AuthenticationService[IO](Mode.Development, config)

  def commandRoutes(engine: ObserveEngine[IO]): IO[HttpRoutes[IO]] =
    IO(ObserveCommandRoutes(engine).service)

//   def uiRoutes(wsb: WebSocketBuilder2[IO]): IO[HttpRoutes[IO]] =
//     for {
//       o  <- Topic[IO, ObserveEvent]
//       cs <- Ref.of[IO, ClientsSetDb.ClientsSet](Map.empty).map(ClientsSetDb.apply[IO](_))
//     } yield new ObserveUIApiRoutes(
//       Site.GS,
//       Mode.Development,
//       authService,
// //                                   GuideConfigDb.constant[IO],
// //                                   statusDb,
//       cs,
//       o,
//       wsb
//     ).service
//
//   def newLoginToken(wsb: WebSocketBuilder2[IO]): IO[String] =
//     for {
//       s <- uiRoutes(wsb)
//       r <- s
//              .apply(
//                Request(method = Method.POST, uri = uri"/observe/login")
//                  .withEntity(UserLoginRequest("telops", "pwd"))
//              )
//              .value
//       k <- r.map(_.cookies).orEmpty.find(_.name === "token").pure[IO]
//     } yield k.map(_.content).orEmpty
}
