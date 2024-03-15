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
import lucuma.ui.sso.SSOClient
import lucuma.ui.sso.SSOConfig
import lucuma.ui.sso.UserVault
import lucuma.ui.syntax.all.*
import observe.model.ClientConfig
import observe.model.events.client.ClientEvent
import observe.queries.ObsQueriesGQL
import observe.ui.BroadcastEvent
import observe.ui.DefaultErrorPolicy
import observe.ui.ObserveStyles
import observe.ui.components.services.ObservationSyncer
import observe.ui.components.services.ServerEventHandler
import observe.ui.model.AppContext
import observe.ui.model.RootModel
import observe.ui.model.RootModelData
import observe.ui.model.enums.*
import observe.ui.services.ConfigApi
import observe.ui.services.*
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy
import org.http4s.client.websocket.WSConnectionHighLevel
import org.http4s.client.websocket.WSFrame
import org.http4s.client.websocket.WSRequest
import org.http4s.client.websocket.middleware.Reconnect
import org.http4s.dom.FetchClientBuilder
import org.http4s.dom.WebSocketClient
import org.http4s.syntax.all.*
import org.scalajs.dom
import org.typelevel.log4cats.Logger
import retry.*
import typings.loglevel.mod.LogLevelDesc

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

object MainApp extends ServerEventHandler:
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

  private def createLogger(level: LogLevelDesc): Logger[IO] =
    LogLevelLogger.setLevel(level)
    LogLevelLogger.createForRoot[IO]

  private given Logger[IO] = createLogger(LogLevelDesc.INFO)

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
  private val FetchRetryPolicy = // http4s-client middleware
    RetryPolicy[IO](RetryPolicy.exponentialBackoff(15.seconds, Int.MaxValue))

  private val WSRetryPolicy = // cats-retry
    RetryPolicies
      .limitRetries[Resource[IO, _]](10)
      .join(RetryPolicies.exponentialBackoff[Resource[IO, _]](10.milliseconds))

  // Build regular HTTP client
  private val fetchClient: Client[IO] =
    Retry(FetchRetryPolicy)(
      FetchClientBuilder[IO]
        .withRequestTimeout(5.seconds)
        .withCache(dom.RequestCache.`no-store`)
        .create
    )

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

  // Initialization consists of:
  //   (1) Establishing a connection to the server via WebSocket
  //   (2) Setup server event processing
  //   (3) Receive server initialization event, including ODB and SSO configuration
  //   (4) Setup ODB client and build AppContext
  //   (5) Get user from cookie and validate with SSO
  //   (6) Initialize ODB client WebSocket connection
  //   (7) Query ready observations on ODB
  private val component =
    ScalaFnComponent
      .withHooks[Unit]
      .useToastRef
      .useStateView(none[SyncStatus])          // UI is synced with server
      .useSingleEffect
      .useResourceOnMountBy: (_, _, syncStatus, _) => // wsConnection to server (1)
        Reconnect(
          retryingOnAllErrors[WSConnectionHighLevel[IO]](
            policy = WSRetryPolicy,
            onError =
              (_: Throwable, _) => syncStatus.async.set(SyncStatus.OutOfSync.some).toResource
          )(WebSocketClient[IO].connectHighLevel(WSRequest(EventWsUri)))
        ).map(_.some)
      .useStateView(Pot.pending[ClientConfig]) // clientConfigPot
      .useStateView(RootModelData.Initial)     // rootModelData
      .useStateView(ApiStatus.Idle)            // configApiStatus
      .useAsyncEffectWhenDepsReady((_, _, _, _, wsConnection, _, _, _) =>
        wsConnection.flatMap(_.toPot)
      ): (_, _, syncStatus, _, _, clientConfig, rootModelData, configApiStatus) =>
        _.receiveStream  // Setup server event processor (2)
          .through(parseClientEvents)
          .evalMap:
            case Right(event) =>
              processStreamEvent(clientConfig, rootModelData, syncStatus, configApiStatus)(event)
            case Left(error)  => processStreamError(rootModelData)(error)
          .compile
          .drain
          .start
          .map(_.cancel) // Ensure fiber is cancelled when effect is re-run
      .useState(Pot.pending[AppContext[IO]])   // ctxPot
      .useAsyncEffectWhenDepsReady((_, _, _, _, _, clientConfig, _, _, _) => clientConfig.get):
        (_, toastRef, _, _, _, _, _, _, ctxPot) => // Build AppContext (4)
          clientConfig =>
            val ctxResource: Resource[IO, AppContext[IO]] =
              (for
                dispatcher                                 <- Dispatcher.parallel[IO]
                given WebSocketJSBackend[IO]                = WebSocketJSBackend[IO](dispatcher)
                given WebSocketJSClient[IO, ObservationDB] <-
                  Resource.eval:
                    WebSocketJSClient.of[IO, ObservationDB](
                      clientConfig.odbUri.toString,
                      "ODB",
                      reconnectionStrategy
                    )
              yield AppContext[IO](
                AppContext.version(clientConfig.environment),
                SSOClient(SSOConfig(clientConfig.ssoUri)),
                (tab: AppTab) => MainApp.routerCtl.urlFor(tab.getPage).value,
                (tab: AppTab, via: SetRouteVia) => MainApp.routerCtl.set(tab.getPage, via),
                toastRef
              )).onError:
                case t => Resource.eval(IO(t.printStackTrace()))

            ctxResource.allocated.flatMap: (ctx, release) =>
              ctxPot.setStateAsync(ctx.ready).as(release) // Return `release` as cleanup effect
      .useEffectWhenDepsReady((_, _, _, _, _, _, _, _, ctxPot) => ctxPot.value):
        (_, _, _, _, _, _, rootModelData, _, _) =>
          ctx => // Once AppContext is ready, proceed to attempt login (5)
            enforceStaffRole(ctx.ssoClient).attempt.flatMap: userVault =>
              rootModelData.async.mod(_.withLoginResult(userVault))
      .useAsyncEffectWhenDepsReady((_, _, _, _, _, _, rootModelData, _, ctxPot) =>
        (ctxPot.value, rootModelData.get.userVault.map(_.toPot).flatten).tupled
      ): (_, _, _, _, _, _, _, _, _) => // Initialize ODB client (6)
        (ctx, userVault) =>
          ctx
            .initODBClient(Map("Authorization" -> userVault.authorizationHeader.asJson))
            .as(ctx.closeODBClient) // Disconnect on logout
      // Subscribe to client event stream (and initialize ClientConfig)
      .localValBy: (_, toastRef, syncStatus, _, _, clientConfigPot, rootModelData, _, ctxPot) =>
        (rootModelData.get.userVault.toOption.flatMap(_.map(_.token)),
         ctxPot.value.toOption,
         clientConfigPot.get.toOption
        ).mapN: (token, ctx, clientConfig) =>
          ApiClient(
            fetchClient,
            ApiBasePath,
            clientConfig.clientId,
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
                syncStatus.async.set(SyncStatus.OutOfSync.some) // Triggers reSync
          )
      // If SyncStatus goes OutOfSync, start reSync (or cancel if it goes back to Synced)
      .useEffectWithDepsBy((_, _, syncStatus, _, _, _, _, _, _, _) => syncStatus.get):
        (_, _, _, singleDispatcher, _, _, _, _, _, apiClientOpt) =>
          case Some(SyncStatus.OutOfSync) =>
            apiClientOpt
              .map: client =>
                singleDispatcher.submit(client.refresh)
              .orEmpty
          case Some(SyncStatus.Synced)    => singleDispatcher.cancel
          case _                          => IO.unit
      .useStreamBy((_, _, _, _, _, _, _, _, ctxPot, _) => ctxPot.value.void):
        (_, _, _, _, _, _, _, _, ctxPot, _) =>
          _ =>
            ctxPot.value
              .map(_.odbClient)
              .toOption
              .foldMap(_.statusStream) // Track ODB initialization status
      .useRef(false)
      .useAsyncEffectWhenDepsReady((_, _, _, _, _, _, _, _, ctxPot, _, odbStatus, _) =>
        (ctxPot.value, odbStatus.toPot.filter(_ === PersistentClientStatus.Initialized)).tupled
      ): (_, _, _, _, _, _, rootModelData, _, _, _, _, subscribed) =>
        (ctx, _) => // Query ready observations (7)
          import ctx.given

          if (!subscribed.value)
            val readyObservations = rootModelData
              .zoom(RootModelData.readyObservations)
              .async

            // TODO RECONNECT ON ERRORS
            val obsSummaryUpdaterResource =
              for
                _         <- Resource.eval(IO.println("SUBSCRIBING!!!!"))
                obsStream <-
                  ObsQueriesGQL
                    .ActiveObservationIdsQuery[IO]
                    .query()
                    .flatMap(data => readyObservations.set(data.observations.matches.ready))
                    .recoverWith(t => readyObservations.set(Pot.error(t)))
                    .reRunOnResourceSignals(
                      ObsQueriesGQL.ObservationEditSubscription.subscribe[IO]()
                    )
                _         <- Resource.make(obsStream.compile.drain.start)(_.cancel)
              yield ()

            subscribed.setAsync(true) >>
              obsSummaryUpdaterResource.allocated
                .map((_, close) => close)
          else IO(IO.unit)
      .useEffectResultOnMount(Semaphore[IO](1).map(_.permit))
      .render:
        (
          _,
          toastRef,
          syncStatus,
          _,
          _,
          clientConfigPot,
          rootModelData,
          configApiStatus,
          ctxPot,
          apiClientOpt,
          _,
          _,
          permitPot
        ) =>
          val apisOpt: Option[(ConfigApi[IO], SequenceApi[IO])] =
            (apiClientOpt, rootModelData.get.observer, permitPot.toOption).mapN:
              (client, observer, permit) =>
                (
                  ConfigApiImpl(client = client, apiStatus = configApiStatus, latch = permit),
                  SequenceApiImpl(
                    client = client,
                    observer = observer,
                    requests = rootModelData.zoom(RootModelData.obsRequests)
                  )
                )

          def provideApiCtx(children: VdomNode*) =
            apisOpt.fold(React.Fragment(children*)): (configApi, sequenceApi) =>
              ConfigApi.ctx.provide(configApi)(SequenceApi.ctx.provide(sequenceApi)(children*))

          val ResyncingPopup =
            Dialog(
              header = "Reestablishing connection to server...",
              closable = false,
              visible = syncStatus.get.contains(SyncStatus.OutOfSync),
              onHide = Callback.empty,
              clazz = ObserveStyles.SyncingPanel
            )(
              SolarProgress(),
              Button("Refresh page instead", onClick = Callback(dom.window.location.reload()))
            )

          // When both AppContext and UserVault are ready, proceed to render.
          (ctxPot.value, rootModelData.zoom(RootModelData.userVault).toPotView).tupled.renderPot:
            (ctx, userVault) =>
              AppContext.ctx.provide(ctx)(
                IfLogged[BroadcastEvent](
                  "Observe".refined,
                  Css.Empty,
                  allowGuest = false,
                  ctx.ssoClient,
                  userVault,
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
                    router(RootModel(clientConfigPot.get, rootModelData))
                  )
                )
              )

  inline def apply() = component()
