// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.effect.IO
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.effect.std.Semaphore
import cats.syntax.all.*
import clue.PersistentClientStatus
import clue.js.WebSocketJSBackend
import clue.js.WebSocketJSClient
import clue.websocket.ReconnectionStrategy
import crystal.Pot
import crystal.PotOption
import crystal.react.*
import crystal.react.given
import crystal.react.hooks.*
import crystal.syntax.*
import eu.timepit.refined.types.string.NonEmptyString
import fs2.Pipe
import io.circe.parser.decode
import io.circe.syntax.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.extra.router.*
import japgolly.scalajs.react.vdom.html_<^.*
import log4cats.loglevel.LogLevelLogger
import lucuma.core.model.StandardRole
import lucuma.core.model.StandardUser
import lucuma.react.common.*
import lucuma.react.primereact.Button
import lucuma.react.primereact.Dialog
import lucuma.react.primereact.Message
import lucuma.react.primereact.MessageItem
import lucuma.react.primereact.hooks.all.*
import lucuma.refined.*
import lucuma.schemas.ObservationDB
import lucuma.ui.components.SolarProgress
import lucuma.ui.components.state.IfLogged
import lucuma.ui.reusability.given
import lucuma.ui.sso.SSOClient
import lucuma.ui.sso.UserVault
import lucuma.ui.syntax.pot.*
import observe.model.Environment
import observe.model.events.client.ClientEvent
import observe.ui.BroadcastEvent
import observe.ui.ObserveStyles
import observe.ui.components.services.ObservationSyncer
import observe.ui.components.services.ServerEventHandler
import observe.ui.model.AppConfig
import observe.ui.model.AppContext
import observe.ui.model.RootModel
import observe.ui.model.RootModelData
import observe.ui.model.enums.*
import observe.ui.model.reusability.given
import observe.ui.services.ConfigApi
import observe.ui.services.*
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

object MainApp extends ServerEventHandler:
  private val ConfigFile: Uri       = uri"/environments.conf.json"
  private val ApiBasePath: Uri.Path = path"/api/observe/"
  private val EventWsUri: Uri       =
    Uri(
      scheme"wss".some,
      Uri
        .Authority(
          host = Uri.Host.unsafeFromString(dom.window.location.hostname),
          port = dom.window.location.port.toIntOption
        )
        .some,
      path = ApiBasePath / "events"
    )

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

  // Log in from cookie and switch to staff role
  private def enforceStaffRole(ssoClient: SSOClient[IO]): IO[Option[UserVault]] =
    ssoClient.whoami
      .flatMap: userVault =>
        userVault.map(_.user) match
          case Some(StandardUser(_, role, other, _)) =>
            (role +: other)
              .collectFirst { case StandardRole.Staff(roleId) => roleId }
              .fold(IO(userVault))(ssoClient.switchRole)
          // .map(_.orElse(throw new Exception("User is not staff")))
          case _                                     => IO(userVault)

  // Turn a Stream[WSFrame] into Stream[ClientEvent]
  val parseClientEvents: Pipe[IO, WSFrame, Either[Throwable, ClientEvent]] =
    _.flatMap:
      case WSFrame.Text(text, _) => fs2.Stream(decode[ClientEvent](text))
      case _                     => fs2.Stream.empty

  private val component =
    ScalaFnComponent
      .withHooks[Unit]
      .useToastRef
      .useStateView(none[SyncStatus]) // UI is synced with server
      .useSingleEffect
      .useResourceOnMountBy: (_, toastRef, _, _) => // Build AppContext
        (for
          appConfig                                  <- Resource.eval(fetchConfig)
          given Logger[IO]                           <- Resource.eval(setupLogger(LogLevelDesc.INFO))
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
        )).onError:
          case t => Resource.eval(IO(t.printStackTrace()))
      .useStateView(Pot.pending[RootModelData])
      .useEffectWhenDepsReady((_, _, _, _, ctxPot, _) => ctxPot): (_, _, _, _, _, rootModelData) =>
        ctx => // Once AppContext is ready, proceed to attempt login.
          import ctx.given

          enforceStaffRole(ctx.ssoClient).attempt
            .flatMap: userVault =>
              rootModelData.async.set(RootModelData.initial(userVault).ready)
      .useAsyncEffectWithDepsBy((_, _, _, _, ctxPot, rootModelData) =>
        (ctxPot.void, rootModelData.get.map(_.userVault))
      )((_, _, _, _, ctxPot, rootModelData) =>
        _ =>
          (ctxPot.toOption, rootModelData.get.toOption.flatMap(_.userVault))
            .mapN: (ctx, userVault) =>
              ctx
                .initODBClient(Map("Authorization" -> userVault.authorizationHeader.asJson))
                .as(ctx.closeODBClient) // Disconnect on logout
            .orEmpty
      )
      .useStateView(Pot.pending[Environment])
      // Subscribe to client event stream (and initialize Environment)
      // TODO Reconnecting middleware
      .localValBy: (_, toastRef, isSynced, _, ctxPot, rootModelDataPot, environmentPot) =>
        (rootModelDataPot.toPotView.toOption,
         rootModelDataPot.get.toOption.flatMap(_.userVault.map(_.token)),
         ctxPot.toOption,
         environmentPot.get.toOption
        ).mapN: (rootModelData, token, ctx, environment) =>
          import ctx.given

          ApiClient(
            fetchClient,
            ApiBasePath,
            environment.clientId,
            token,
            t =>
              toastRef
                .show:
                  MessageItem(
                    id = "configApiError",
                    content = "Error saving changes",
                    severity = Message.Severity.Error
                  )
                .to[IO] >>
                rootModelData.async
                  .zoom(RootModelData.log)
                  .mod(_ :+ NonEmptyString.unsafeFrom(t.getMessage)) >>
                IO.println(t.getMessage) >>
                isSynced.async.set(SyncStatus.OutOfSync.some) // Triggers reSync
          )
      // Connection to event stream is surrogated to ODB WS connection,
      // only established whenever ODB WS is connected and initialized.
      .useStreamBy((_, _, _, _, ctxPot, _, _, _) => ctxPot.void): (_, _, _, _, ctxPot, _, _, _) =>
        _ => ctxPot.map(_.odbClient).toOption.foldMap(_.statusStream)
      .useResourceBy((_, _, _, _, _, _, _, _, odbStatus) => odbStatus):
        (_, _, _, _, _, _, _, _, _) =>
          case PotOption.ReadySome(PersistentClientStatus.Initialized) =>
            // Reconnect(WebSocketClient[IO]).connectHighLevel(WSRequest(EventWsUri))
            WebSocketClient[IO].connectHighLevel(WSRequest(EventWsUri)).map(_.some)
          case _                                                       => Resource.pure(none)
      // If SyncStatus goes OutOfSync, start reSync (or cancel if it goes back to Synced)
      .useEffectWithDepsBy((_, _, syncStatus, _, _, _, _, _, _, _) => syncStatus.get):
        (_, _, syncStatus, singleDispatcher, _, _, _, apiClientOpt, _, _) =>
          case Some(SyncStatus.OutOfSync) =>
            apiClientOpt
              .map: client =>
                singleDispatcher.submit(client.refresh)
              .orEmpty
          case Some(SyncStatus.Synced)    => singleDispatcher.cancel
          case _                          => IO.unit
      .useStateView(ApiStatus.Idle)   // configApiStatus
      .useAsyncEffectWhenDepsReady(
        (_, _, _, _, ctxPot, rootModelDataPot, _, _, _, wsConnection, _) =>
          (wsConnection.flatMap(_.toPot), rootModelDataPot.toPotView, ctxPot).tupled
      ): (_, _, syncStatus, _, _, _, environment, _, _, _, configApiStatus) =>
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
            .map(_.cancel) // Previous fiber is cancelled when effect is re-run
      .useEffectResultOnMount(Semaphore[IO](1).map(_.permit))
      .render:
        (
          _,
          toastRef,
          isSynced,
          _,
          ctxPot,
          rootModelDataPot,
          environmentPot,
          apiClientOpt,
          _,
          _,
          configApiStatus,
          permitPot
        ) =>
          val apisOpt: Option[(ConfigApi[IO], SequenceApi[IO])] =
            (apiClientOpt,
             rootModelDataPot.get.toOption.flatMap(_.observer),
             permitPot.toOption,
             ctxPot.toOption
            ).mapN: (client, observer, permit, ctx) =>
              import ctx.given
              (
                ConfigApiImpl(client = client, apiStatus = configApiStatus, latch = permit),
                SequenceApiImpl(client = client, observer = observer)
              )

          def provideApiCtx(children: VdomNode*) =
            apisOpt.fold(React.Fragment(children: _*)): (configApi, sequenceApi) =>
              ConfigApi.ctx.provide(configApi)(SequenceApi.ctx.provide(sequenceApi)(children: _*))

          val ResyncingPopup =
            Dialog(
              header = "Reestablishing connection to server...",
              closable = false,
              visible = isSynced.get.contains(SyncStatus.OutOfSync),
              onHide = Callback.empty,
              clazz = ObserveStyles.SyncingPanel
            )(
              SolarProgress(),
              Button("Refresh page instead", onClick = Callback(dom.window.location.reload()))
            )

          // When both AppContext and RootModel are ready, proceed to render.
          (ctxPot, rootModelDataPot.toPotView).tupled.renderPot: (ctx, rootModelData) =>
            import ctx.given

            AppContext.ctx.provide(ctx)(
              IfLogged[BroadcastEvent](
                "Observe".refined,
                Css.Empty,
                allowGuest = false,
                ctx.ssoClient,
                rootModelData.zoom(RootModelData.userVault),
                rootModelData.zoom(RootModelData.userSelectionMessage),
                _ => IO.unit, // MainApp takes care of connections
                IO.unit,
                IO.unit,
                "observe".refined,
                _.event === BroadcastEvent.LogoutEventId,
                _.value.toString,
                BroadcastEvent.LogoutEvent(_)
              )(_ =>
                provideApiCtx(
                  ResyncingPopup,
                  ObservationSyncer(rootModelData.zoom(RootModelData.nighttimeObservation)),
                  router(RootModel(environmentPot.get, rootModelData))
                )
              )
            )

  inline def apply() = component()
