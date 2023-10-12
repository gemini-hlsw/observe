// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.effect.IO
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.effect.std.Semaphore
import cats.syntax.all.*
import clue.js.WebSocketJSBackend
import clue.js.WebSocketJSClient
import clue.websocket.ReconnectionStrategy
import crystal.Pot
import crystal.react.*
import crystal.react.hooks.*
import crystal.syntax.*
import eu.timepit.refined.types.string.NonEmptyString
import fs2.Pipe
import io.circe.parser.decode
import japgolly.scalajs.react.*
import japgolly.scalajs.react.extra.router.*
import japgolly.scalajs.react.vdom.html_<^.*
import log4cats.loglevel.LogLevelLogger
import lucuma.core.model.StandardRole
import lucuma.core.model.StandardUser
import lucuma.react.common.*
import lucuma.react.primereact.Dialog
import lucuma.react.primereact.Message
import lucuma.react.primereact.MessageItem
import lucuma.react.primereact.hooks.all.*
import lucuma.schemas.ObservationDB
import lucuma.ui.components.SolarProgress
import lucuma.ui.sso.SSOClient
import lucuma.ui.sso.UserVault
import lucuma.ui.syntax.pot.*
import observe.model.ClientId
import observe.model.Environment
import observe.model.events.client.ClientEvent
import observe.ui.ObserveStyles
import observe.ui.model.AppConfig
import observe.ui.model.AppContext
import observe.ui.model.RootModel
import observe.ui.model.RootModelData
import observe.ui.model.enums.*
import observe.ui.services.ConfigApi
import observe.ui.services.ConfigApiImpl
import org.http4s.Uri
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy
import org.http4s.client.websocket.WSFrame
import org.http4s.client.websocket.WSRequest
import org.http4s.dom.FetchClientBuilder
import org.http4s.dom.WebSocketClient
import org.http4s.syntax.all.*
import org.scalajs.dom
import org.typelevel.log4cats.Logger
import typings.loglevel.mod.LogLevelDesc

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

object MainApp:
  private val ConfigFile: Uri     = uri"/environments.conf.json"
  private val ApiBaseUri: Uri     = uri"/api/observe"
  private val EventWsUri: Uri     =
    Uri.unsafeFromString("wss://" + dom.window.location.host + ApiBaseUri + "/events")
  private val RefreshBaseUri: Uri = ApiBaseUri / "refresh"

  // Set up logging
  private def setupLogger(level: LogLevelDesc): IO[Logger[IO]] = IO:
    LogLevelLogger.setLevel(level)
    LogLevelLogger.createForRoot[IO]

  // Define React routing
  private val (router, routerCtl) =
    RouterWithProps.componentAndCtl(BaseUrl.fromWindowOrigin, Routing.config)

  // Define reconnection strategy for HTTP calls
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

  // Only idempotent requests are retried
  private val FetchRetryPolicy =
    RetryPolicy[IO](RetryPolicy.exponentialBackoff(15.seconds, Int.MaxValue))

  // Build regular HTTP client
  private val fetchClient: Client[IO] =
    Retry(FetchRetryPolicy)(
      FetchClientBuilder[IO]
        .withRequestTimeout(5.seconds)
        .withCache(dom.RequestCache.`no-store`)
        .create
    )

  // Fetch environment configuration (from environments.conf.json)
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

  private def reSync(clientId: ClientId): IO[Unit] =
    fetchClient.get(RefreshBaseUri / clientId.toString)(_ => IO.unit)

  // Log in from cookie and switch to staff role
  private def enforceStaffRole(ssoClient: SSOClient[IO]): IO[Option[UserVault]] =
    ssoClient.whoami.flatMap(userVault =>
      userVault.map(_.user) match
        case Some(StandardUser(_, role, other, _)) =>
          (role +: other)
            .collectFirst { case StandardRole.Staff(roleId) => roleId }
            .fold(IO(userVault))(ssoClient.switchRole)
        // .map(_.orElse(throw new Exception("User is not staff")))
        case _                                     => IO(userVault)
    )

  // Turn a Stream[WSFrame] into Stream[ClientEvent]
  val parseClientEvents: Pipe[IO, WSFrame, Either[Throwable, ClientEvent]] =
    _.flatMap:
      case WSFrame.Text(text, _) => fs2.Stream(decode[ClientEvent](text))
      case _                     => fs2.Stream.empty

  def processStreamEvent(
    environment:     View[Pot[Environment]],
    rootModelData:   View[RootModelData],
    syncStatus:      View[SyncStatus],
    configApiStatus: View[ApiStatus]
  )(
    event:           ClientEvent
  )(using Logger[IO]): IO[Unit] =
    event match
      case ClientEvent.InitialEvent(env)                           =>
        environment.async.set(env.ready)
      case ClientEvent.ObserveState(sequenceExecution, conditions) =>
        rootModelData.zoom(RootModelData.sequenceExecution).async.set(sequenceExecution) >>
          rootModelData.zoom(RootModelData.conditions).async.set(conditions) >>
          syncStatus.async.set(SyncStatus.Synced) >>
          configApiStatus.async.set(ApiStatus.Idle)

  def processStreamError(
    rootModelData: View[RootModelData]
  )(error: Throwable)(using Logger[IO]): IO[Unit] =
    rootModelData
      .zoom(RootModelData.log)
      .async
      .mod(_ :+ NonEmptyString.unsafeFrom(s"ERROR Receiving Client Event: ${error.getMessage}"))

  private val component =
    ScalaFnComponent
      .withHooks[Unit]
      .useToastRef
      .useStateView(SyncStatus.OutOfSync) // UI is synced with server
      .useResourceOnMountBy: (_, toastRef, _) => // Build AppContext
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
          AppContext.version(appConfig.environment),
          SSOClient(appConfig.sso),
          (tab: AppTab) => MainApp.routerCtl.urlFor(tab.getPage).value,
          (tab: AppTab, via: SetRouteVia) => MainApp.routerCtl.set(tab.getPage, via),
          toastRef
        )
      .useStateView(Pot.pending[RootModelData])
      .useEffectWhenDepsReady((_, _, _, ctxPot, _) => ctxPot): (_, _, _, _, rootModelData) =>
        ctx => // Once AppContext is ready, proceed to attempt login.
          import ctx.given

          enforceStaffRole(ctx.ssoClient).attempt
            .flatMap(userVault => rootModelData.async.set(RootModelData.initial(userVault).ready))
      .useStateView(Pot.pending[Environment])
      // Subscribe to client event stream (and initialize Environment)
      // TODO Reconnecting middleware
      .useResourceOnMount:
        // Reconnect(WebSocketClient[IO]).connectHighLevel(WSRequest(EventWsUri))
        // We also have to reSync in case of connection lost
        WebSocketClient[IO].connectHighLevel(WSRequest(EventWsUri))
      .useStateView(ApiStatus.Idle)       // configApiStatus
      .useAsyncEffectWhenDepsReady(
        (_, _, _, ctxPot, rootModelDataPot, environment, wsConnection, _) =>
          (wsConnection, rootModelDataPot.toPotView, ctxPot).tupled
      ): (_, _, syncStatus, _, _, environment, _, configApiStatus) =>
        (wsConnection, rootModelData, ctx) =>
          import ctx.given

          wsConnection.receiveStream
            .through(parseClientEvents)
            .evalMap: // Process client event stream
              case Right(event) =>
                processStreamEvent(environment, rootModelData, syncStatus, configApiStatus)(event)
              case Left(error)  => processStreamError(rootModelData)(error)
            .compile
            .drain
            .start
            .map(_.cancel)
      // RootModel is not initialized until RootModelData and Environment are available
      .useStateView(Pot.pending[RootModel])
      .useEffectWhenDepsReady((_, _, _, _, rootModelData, environment, _, _, _) =>
        (rootModelData.get, environment.get).tupled
      ): (_, _, _, _, _, _, _, _, rootModelPot) =>
        // Once RootModelData and Environment are ready, build RootModel
        (rootModelData, environment) =>
          rootModelPot.set(RootModel(environment, rootModelData).ready)
      .useEffectResultOnMount(Semaphore[IO](1).map(_.permit))
      .render:
        (
          _,
          toastRef,
          isSynced,
          ctxPot,
          _,
          environmentPot,
          _,
          configApiStatus,
          rootModelPot,
          permitPot
        ) =>
          val configApiOpt: Option[ConfigApi[IO]] =
            (rootModelPot.toPotView.toOption,
             rootModelPot.get.toOption.flatMap(_.userVault.map(_.token)),
             permitPot.toOption,
             ctxPot.toOption,
             environmentPot.toPotView.toOption
            ).mapN: (rootModel, token, permit, ctx, environment) =>
              import ctx.given

              ConfigApiImpl(
                client = fetchClient,
                baseUri = ApiBaseUri,
                token = token,
                apiStatus = configApiStatus,
                latch = permit,
                onError = t =>
                  toastRef
                    .show(
                      MessageItem(
                        id = "configApiError",
                        content = "Error saving changes",
                        severity = Message.Severity.Error
                      )
                    )
                    .to[IO] >>
                    isSynced.async.set(SyncStatus.OutOfSync) >>
                    rootModel.async
                      .zoom(RootModel.log)
                      .mod(_ :+ NonEmptyString.unsafeFrom(t.getMessage)) >>
                    reSync(environment.get.clientId)
              )

          def provideApiCtx(children: VdomNode*) =
            configApiOpt.fold(React.Fragment(children: _*))(ConfigApi.ctx.provide(_)(children: _*))

          // When both AppContext and RootModel are ready, proceed to render.
          (ctxPot, rootModelPot.toPotView).tupled.renderPot: (ctx, rootModel) =>
            AppContext.ctx.provide(ctx)(
              provideApiCtx(
                Dialog(
                  header = "Reestablishing connection to server...",
                  closable = false,
                  visible = isSynced.get == SyncStatus.OutOfSync,
                  onHide = Callback.empty,
                  clazz = ObserveStyles.SyncingPanel
                )(SolarProgress()),
                router(rootModel)
              )
            )

  inline def apply() = component()
