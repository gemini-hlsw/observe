// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import cats.effect.IO
import cats.effect.Resource
import cats.effect.Sync
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits._
import cats.syntax.all.*
import clue.js.WebSocketJSBackend
import clue.js.WebSocketJSClient
import clue.websocket.CloseParams
import clue.websocket.ReconnectionStrategy
import crystal.react.hooks.*
import io.circe.Json
import io.circe.syntax.given
import japgolly.scalajs.react.*
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^.*
import log4cats.loglevel.LogLevelLogger
import lucuma.schemas.ObservationDB
import lucuma.ui.enums.Theme
import observe.ui.model.RootModel
import org.scalajs.dom
import org.scalajs.dom.Element
import org.typelevel.log4cats.Logger
import typings.loglevel.mod.LogLevelDesc

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("Main")
object Main {

  @JSExport
  def runIOApp(): Unit = run.unsafeRunAndForget()

  def setupLogger[F[_]: Sync](level: LogLevelDesc): F[Logger[F]] = Sync[F].delay {
    LogLevelLogger.setLevel(level)
    LogLevelLogger.createForRoot[F]
  }

  def setupDOM[F[_]: Sync]: F[Element] = Sync[F].delay(
    Option(dom.document.getElementById("root")).getOrElse {
      val elem = dom.document.createElement("div")
      elem.id = "root"
      dom.document.body.appendChild(elem)
      elem
    }
  )

  val (router, routerCtl) =
    RouterWithProps.componentAndCtl(BaseUrl.fromWindowOrigin, Routing.config)

  val mainApp =
    ScalaFnComponent
      .withHooks[Unit]
      .useStateView(RootModel.Initial)
      .render((_, state) => router(state))

  def buildPage(ctx: AppContext[IO]): IO[Unit] =
    setupDOM[IO].map(node => AppContext.ctx.provide(ctx)(mainApp()).renderIntoDOM(node)).void

  val reconnectionStrategy: ReconnectionStrategy =
    (attempt, reason) =>
      // Web Socket close codes: https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent
      if (reason.toOption.flatMap(_.toOption.flatMap(_.code)).exists(_ === 1000))
        none
      else // Increase the delay to get exponential backoff with a minimum of 1s and a max of 1m
        FiniteDuration(
          math.min(60.0, math.pow(2, attempt.toDouble - 1)).toLong,
          TimeUnit.SECONDS
        ).some

  val initPayload: Map[String, Json] = Map(
    "Authorization" -> "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzUxMiJ9.eyJpc3MiOiJsdWN1bWEtc3NvIiwic3ViIjoiMjc3IiwiYXVkIjoibHVjdW1hIiwiZXhwIjoxNjg4NTgwMDE0LCJuYmYiOjE2ODg1Nzk0MDQsImlhdCI6MTY4ODU3OTQwNCwKICAibHVjdW1hLXVzZXIiIDogewogICAgInR5cGUiIDogInN0YW5kYXJkIiwKICAgICJpZCIgOiAidS0xMTUiLAogICAgInJvbGUiIDogewogICAgICAidHlwZSIgOiAicGkiLAogICAgICAiaWQiIDogInItMTA1IgogICAgfSwKICAgICJvdGhlclJvbGVzIiA6IFsKICAgIF0sCiAgICAicHJvZmlsZSIgOiB7CiAgICAgICJvcmNpZElkIiA6ICIwMDAwLTAwMDMtMzgzNy05OTAxIiwKICAgICAgImdpdmVuTmFtZSIgOiAiUmHDumwiLAogICAgICAiZmFtaWx5TmFtZSIgOiAiUGlhZ2dpbyIsCiAgICAgICJjcmVkaXROYW1lIiA6IG51bGwsCiAgICAgICJwcmltYXJ5RW1haWwiIDogbnVsbAogICAgfQogIH0KfQ.YTgdAzKoG56p1PdEGBLtuxb52aM1GeCBUX_sp_oPoD6mf6jqJxoUWMIOC7JK5eh6b8Vo-ez4pqIxGRZ2HZ5TOqTBNDdPhsqB6XKznyEnOrLUp8FazWDIdpwEHg3rEWsDF0fld_rH71xPIPLZYj8bDh2mViuZ84lXTd4-Yyw2RphKPcTxozWNEijI4bn5spR7UEjYbXHClmDFA8EwKBYiNZtMj1FisKwl0miibDHrlCslpTcAUYJ-gnCrypRWQ6LWsNxsIojCjiF3HCBN-XRfQ37T43Tpdi7tNqJID3v_D2A-CbeqyyNUnO4LiXTcjqvkserDYg0QlwLk3lBjZaHi46mvijtZbveNDBG7mzCOYlatlefERqq_d2sh8XlTstjqE0gBieVfwTaR2tU6OUAwcqaUuFbHJ0gbDpNu8XLI_HaINWi3FGIoR9zHZfJYoScOLxhRDbLK2vRWQEMvIo3QOtIWls6pEJv9qVwkscfTaRAJB0m_WG2GMBMe2626iKNSot37_TgWEshBsFCPOwN_hmzQN__xYL-4xrINx8gXb6wVTXPdvwfMwNlhWuOUXSUmzsb1wwqeSGCxjQKNwVSc5MU27uCjQU404Fz3ESWYwROlpcoDrAU-eF26Rw3kkTvqWK-PGQX3oE75iCZQtcEp1L5bGS4sVuHXIYDi8WALsIQ".asJson
  )

  val buildContext: Resource[IO, AppContext[IO]] =
    for {
      given Logger[IO]            <- Resource.eval(setupLogger[IO](LogLevelDesc.DEBUG))
      dispatcher                  <- Dispatcher.parallel[IO]
      given WebSocketJSBackend[IO] = WebSocketJSBackend[IO](dispatcher)
      odbClient                   <-
        Resource
          .eval(
            WebSocketJSClient.of[IO, ObservationDB](
              "ws://localhost:8082/ws",
              "ODB",
              reconnectionStrategy
            )
          )
      autoInitClient              <-
        Resource.make(
          odbClient.connect() >> odbClient.initialize(initPayload).as(odbClient)
        )(_ => odbClient.terminate() >> odbClient.disconnect(CloseParams(code = 1000)))
    } yield AppContext[IO](summon[Logger[IO]], autoInitClient)

  def run: IO[Unit] =
    (for {
      _   <- Resource.eval(Theme.Light.setup[IO]) // Theme.init[IO] (starts in Dark mode)
      ctx <- buildContext
      _   <- Resource.eval(buildPage(ctx))
    } yield ()).useForever
}
