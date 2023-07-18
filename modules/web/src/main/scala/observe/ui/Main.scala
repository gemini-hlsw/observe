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
    "Authorization" -> "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzUxMiJ9.eyJpc3MiOiJsdWN1bWEtc3NvIiwic3ViIjoiMjc3IiwiYXVkIjoibHVjdW1hIiwiZXhwIjoxNjg5NjI2OTk4LCJuYmYiOjE2ODk2MjYzODgsImlhdCI6MTY4OTYyNjM4OCwKICAibHVjdW1hLXVzZXIiIDogewogICAgInR5cGUiIDogInN0YW5kYXJkIiwKICAgICJpZCIgOiAidS0xMTUiLAogICAgInJvbGUiIDogewogICAgICAidHlwZSIgOiAicGkiLAogICAgICAiaWQiIDogInItMTA1IgogICAgfSwKICAgICJvdGhlclJvbGVzIiA6IFsKICAgIF0sCiAgICAicHJvZmlsZSIgOiB7CiAgICAgICJvcmNpZElkIiA6ICIwMDAwLTAwMDMtMzgzNy05OTAxIiwKICAgICAgImdpdmVuTmFtZSIgOiAiUmHDumwiLAogICAgICAiZmFtaWx5TmFtZSIgOiAiUGlhZ2dpbyIsCiAgICAgICJjcmVkaXROYW1lIiA6IG51bGwsCiAgICAgICJwcmltYXJ5RW1haWwiIDogbnVsbAogICAgfQogIH0KfQ.oXChTU9emcjnvX3yVB0_91_jrK9WfX7fWfl0Kgg9sjUZfZO9jcqRtyxvUmW6GXq6kMf7KFAwdTfddoG-qaCKrYuQ1pimhynpDud8BsHyrt3a39nP7-Zpee2a5asvCm0ZDbvRs-Tziig1ErFxiltb7LMV075PpTQ777_5MnZNNCPqPpr29ZR4NhL3CRMsoqhi6ztcJPR89HNeGbwfaXIua2Pi_FG3hDKDFIR7K9gOwC51qNf8-O_5-jA0Er4_euWVyVWQYBdcbnT90bjoSws63e-ou4L7uQBKhsyFhLNr1LLUj9aB5tFBDghhShYWejzL_a8pWv9zuqp1-ijThrYdXfRuMBkPAjmSjPKDhrJ4UMDb8HqR_W8aam3x798ARXZ3HipFv2kgD3dH6XyzRkLD_3pm1B-mD3vGGOBY4BP0-p_l7F2UFYC2LMmrVfsZqWjyjM5Ie4BixY7PcVpHtEY5_rvymfrc5nFzo3KNUuqlR15K9r0vmhTIdbO_laNgwIQiWtFUQAHUpYKU8Ls5nA8HkTL-Gra7TRG8CwFSXLw9kYxEFWBjcxJTXCEmh2K00WTZT8sy8KRr_UWTrjRMFPeBRVkyp5trzjCw40bAC7CUjDd76J182UjLiSpIFVymh5yyqnjDm1bGP4wpy8YlH5X4YakiNsymgHZRXqE7scpEk54".asJson
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
