// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Sync
import cats.effect.unsafe.implicits._
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

  def buildPage(implicit logger: Logger[IO]): IO[Unit] =
    (for {
      ctx  <- IO(AppContext[IO]())
      node <- setupDOM[IO]
    } yield AppContext.ctx.provide(ctx)(mainApp()).renderIntoDOM(node)).void

  def run: IO[Unit] =
    for {
      logger <- setupLogger[IO](LogLevelDesc.DEBUG)
      _      <- Theme.Light.setup[IO] // Theme.init[IO] (starts in Dark mode)
      _      <- buildPage(logger)
    } yield ()
}
