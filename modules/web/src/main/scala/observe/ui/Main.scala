// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import cats.syntax.all.*
import cats.effect.std.Dispatcher
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.Sync
import cats.effect.unsafe.implicits._
import clue.websocket.CloseParams
import crystal.react.hooks.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.vdom.html_<^.*
import log4cats.loglevel.LogLevelLogger
import lucuma.ui.enums.Theme
import observe.ui.model.RootModel
import org.scalajs.dom
import org.scalajs.dom.Element
import org.typelevel.log4cats.Logger
import typings.loglevel.mod.LogLevelDesc
import clue.js.WebSocketJSClient
import lucuma.schemas.ObservationDB
import clue.websocket.ReconnectionStrategy
import clue.js.WebSocketJSBackend

import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import io.circe.Json
import io.circe.syntax.given

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
    "Authorization" -> "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzUxMiJ9.eyJpc3MiOiJsdWN1bWEtc3NvIiwic3ViIjoiMjc3IiwiYXVkIjoibHVjdW1hIiwiZXhwIjoxNjg3OTg4NTUwLCJuYmYiOjE2ODc5ODc5NDAsImlhdCI6MTY4Nzk4Nzk0MCwKICAibHVjdW1hLXVzZXIiIDogewogICAgInR5cGUiIDogInN0YW5kYXJkIiwKICAgICJpZCIgOiAidS0xMTUiLAogICAgInJvbGUiIDogewogICAgICAidHlwZSIgOiAicGkiLAogICAgICAiaWQiIDogInItMTA1IgogICAgfSwKICAgICJvdGhlclJvbGVzIiA6IFsKICAgIF0sCiAgICAicHJvZmlsZSIgOiB7CiAgICAgICJvcmNpZElkIiA6ICIwMDAwLTAwMDMtMzgzNy05OTAxIiwKICAgICAgImdpdmVuTmFtZSIgOiAiUmHDumwiLAogICAgICAiZmFtaWx5TmFtZSIgOiAiUGlhZ2dpbyIsCiAgICAgICJjcmVkaXROYW1lIiA6IG51bGwsCiAgICAgICJwcmltYXJ5RW1haWwiIDogbnVsbAogICAgfQogIH0KfQ.G3EYcNtZNlNJATiH04lkV6hxdb5RovU5Dz6ZHTiSEgBDpw6g1gBeUfqlztTpsgUiEq7p9jbJj5uXgzKVsfaaWwVNeA8RMtZzMNIJ9POUc22040V0fs10EGOdFaOMEmUlqNtJpbIBJPeljQ4-X3M_Ap9hybnu3FsWTarg6nDzB0bBTKWST-ZuUOSo89YGlWZPSHZF1vqzxe0GKYtjROKuZLdN1wE86mHaY-GcpJT62V5d-sd9Z0X_eHxzriw8yizVWsmqNcuLJe9wZOe1Ut7yKu7wdbDmy8GcmXeDnjdczeT3rDkV6RStyepU7C_Ap1E6kRaTMPfGuhi-fiu7NVa-5Jb6sX-USbtIoUa_Ae409Eu0A-gsfmOzIq4WmAHDODV9XDDdpUqoHSVdeT2n6PE_fB-jRykVaqy-BOlbCUn1k1ZHkK1k-_OB1mG7zRXOGCL5RXBflz3bJvrVTQFO6tkjYTsCui00Ag9a3GZmsr8xp0sEuSuq6kPYGeFYsT1edSxpjfHvybgX0dcqmKsxVM13_drX5WQMJNtDLtHAILSK3vjNlIy849uHPZEJR-tJ0aOAycC2TYQ8-F7ZCwdhyVBwzI_rIz6a83Xcygd8VsWIIOob-tDnWPASQK3e8CJh8hldimDN6vYig9i5teF-2uX5K5-JzHA5yhLYDM81khJnJsg".asJson
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
