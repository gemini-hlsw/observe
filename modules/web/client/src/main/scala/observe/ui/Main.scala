// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import cats.effect.IO
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits._
import cats.syntax.all.*
import clue.js.WebSocketJSBackend
import clue.js.WebSocketJSClient
import clue.websocket.ReconnectionStrategy
import crystal.react.*
import crystal.react.hooks.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^.*
import log4cats.loglevel.LogLevelLogger
import lucuma.core.model.StandardRole
import lucuma.core.model.StandardUser
import lucuma.schemas.ObservationDB
import lucuma.ui.enums.ExecutionEnvironment
import lucuma.ui.sso.SSOClient
import lucuma.ui.sso.UserVault
import observe.ui.model.RootModel
import observe.ui.model.enums.AppTab
import observe.ui.services.ConfigApiImpl
import org.http4s.Uri
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.dom.FetchClientBuilder
import org.http4s.syntax.all.*
import org.scalajs.dom
import org.scalajs.dom.Element
import org.typelevel.log4cats.Logger
import typings.loglevel.mod.LogLevelDesc

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.*
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("Main")
object Main:
  private val ConfigFile = uri"/environments.conf.json"
  private val ApiBaseUri = uri"/api/observe"

  @JSExport
  def runIOApp(): Unit = run.unsafeRunAndForget()

  private def setupLogger(level: LogLevelDesc): IO[Logger[IO]] = IO:
    LogLevelLogger.setLevel(level)
    LogLevelLogger.createForRoot[IO]

  private val setupDOM: IO[Element] = IO:
    Option(dom.document.getElementById("root")).getOrElse:
      val elem = dom.document.createElement("div")
      elem.id = "root"
      dom.document.body.appendChild(elem)
      elem

  private val (router, routerCtl) =
    RouterWithProps.componentAndCtl(BaseUrl.fromWindowOrigin, Routing.config)

  private val mainApp =
    ScalaFnComponent
      .withHooks[Either[Throwable, Option[UserVault]]]
      .useStateViewBy(RootModel.initial(_))
      .render: (_, rootModel) =>
        router(rootModel)

  private def enforceStaffRole(ctx: AppContext[IO]): IO[Option[UserVault]] =
    ctx.ssoClient.whoami.flatMap(userVault =>
      userVault.map(_.user) match
        case Some(StandardUser(_, role, other, _)) =>
          (role +: other)
            .collectFirst { case StandardRole.Staff(roleId) => roleId }
            .fold(IO(userVault))(ctx.ssoClient.switchRole)
        // .map(_.orElse(throw new Exception("User is not staff")))
        case _                                     => IO(userVault)
    )

  private def buildPage(ctx: AppContext[IO]): IO[Unit] =
    (setupDOM, enforceStaffRole(ctx).attempt)
      .parMapN((node, userVault) =>
        AppContext.ctx.provide(ctx)(mainApp(userVault)).renderIntoDOM(node)
      )
      .void

  private val reconnectionStrategy: ReconnectionStrategy =
    (attempt, reason) =>
      // Web Socket close codes: https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent
      if (reason.toOption.flatMap(_.toOption.flatMap(_.code)).exists(_ === 1000))
        none
      else // Increase the delay to get exponential backoff with a minimum of 1s and a max of 1m
        FiniteDuration(
          math.min(60.0, math.pow(2, attempt.toDouble - 1)).toLong,
          TimeUnit.SECONDS
        ).some

  private val fetchClient: Client[IO] =
    FetchClientBuilder[IO]
      .withRequestTimeout(5.seconds)
      .withCache(dom.RequestCache.`no-store`)
      .create

  private val fetchConfig: IO[AppConfig] =
    fetchClient
      .get(ConfigFile)(_.decodeJson[List[AppConfig]])
      .adaptError: t =>
        new Exception("Could not retrieve configuration.", t)
      .flatMap: confs =>
        IO.fromOption(
          confs
            .find: conf =>
              dom.window.location.host.startsWith(conf.hostName)
            .orElse:
              confs.find(_.hostName === "*")
        )(orElse = new Exception("Host not found in configuration."))

  private val buildContext: Resource[IO, AppContext[IO]] =
    for
      appConfig                                  <- Resource.eval(fetchConfig)
      given Logger[IO]                           <- Resource.eval(setupLogger(LogLevelDesc.DEBUG))
      dispatcher                                 <- Dispatcher.parallel[IO]
      given WebSocketJSBackend[IO]                = WebSocketJSBackend[IO](dispatcher)
      given WebSocketJSClient[IO, ObservationDB] <-
        Resource
          .eval(
            WebSocketJSClient.of[IO, ObservationDB](
              appConfig.odbURI.toString,
              "ODB",
              reconnectionStrategy
            )
          )
    yield AppContext[IO](
      AppContext.version(ExecutionEnvironment.Development),
      SSOClient(appConfig.sso),
      ConfigApiImpl(fetchClient, ApiBaseUri),
      // uri"/api/observe"),
      (tab: AppTab) => routerCtl.urlFor(tab.getPage).value,
      (tab: AppTab, via: SetRouteVia) => routerCtl.set(tab.getPage, via)
    )

  private def run: IO[Unit] =
    (for
      ctx <- buildContext
      _   <- Resource.eval(buildPage(ctx))
    yield ()).useForever
