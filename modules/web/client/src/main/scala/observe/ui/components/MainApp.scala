// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.effect.IO
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.effect.std.Semaphore
import cats.syntax.all.*
import clue.PersistentClientStatus
import clue.js.WebSocketJsBackend
import clue.js.WebSocketJsClient
import clue.websocket.ReconnectionStrategy
import crystal.Pot
import crystal.ViewF
import crystal.react.*
import crystal.react.given
import crystal.react.hooks.*
import crystal.syntax.*
import fs2.Pipe
import io.circe.parser.decode
import io.circe.syntax.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.extra.router.*
import japgolly.scalajs.react.vdom.html_<^.*
import log4cats.loglevel.LogLevelLogger
import lucuma.core.model.LocalObservingNight
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
import lucuma.ui.sso.*
import lucuma.ui.syntax.all.*
import observe.model.*
import observe.model.LogMessage
import observe.model.enums.ObserveLogLevel
import observe.model.events.ClientEvent
import observe.queries.ObsQueriesGQL
import observe.ui.BroadcastEvent
import observe.ui.ObserveStyles
import observe.ui.components.services.ObservationSyncer
import observe.ui.components.services.ServerEventHandler
import observe.ui.model.AppContext
import observe.ui.model.ObsSummary
import observe.ui.model.RootModel
import observe.ui.model.RootModelData
import observe.ui.model.enums.*
import observe.ui.services.*
import observe.ui.services.ConfigApi
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
import org.http4s.headers.Authorization
import org.http4s.syntax.all.*
import org.scalajs.dom
import org.typelevel.log4cats.Logger
import retry.*
import typings.loglevel.mod.LogLevelDesc

import java.time.Instant
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

  // Build regular HTTP client for invoking backend.
  private val fetchClient: Client[IO] =
    Retry(FetchRetryPolicy)(
      FetchClientBuilder[IO]
        .withRequestTimeout(5.seconds)
        .withCache(dom.RequestCache.`no-store`)
        .create
    )

  // HTTP client for one-shots to other sites, like the archive. No retries.
  private val plainFetchClient: Client[IO] =
    FetchClientBuilder[IO]
      .withRequestTimeout(2.seconds)
      .withCache(dom.RequestCache.`no-store`)
      .create

  // Log in from cookie and switch to staff role
  private def enforceStaffRole(ssoClient: SSOClient[IO]): IO[UserVault] =
    ssoClient.whoami
      .flatMap: userVault =>
        userVault.map(_.user) match
          case Some(StandardUser(_, role, other, _)) =>
            (role +: other)
              .collectFirst { case StandardRole.Staff(roleId) => roleId }
              .fold(IO.none)(ssoClient.switchRole)
              .map:
                _.getOrElse(throw new Exception("User is not staff"))
          case _                                     =>
            IO.raiseError(new Exception("Unrecognized user"))

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
    ScalaFnComponent[Unit] { _ =>
      for
        toastRef         <- useToastRef
        syncStatus       <- useStateView(none[SyncStatus]) // UI is synced with server
        singleDispatcher <- useSingleEffect
        wsConnection     <-
          useResourceOnMount: // wsConnection to server (1)
            Reconnect(
              retryingOnAllErrors[WSConnectionHighLevel[IO]](
                policy = WSRetryPolicy,
                onError =
                  (_: Throwable, _) => syncStatus.async.set(SyncStatus.OutOfSync.some).toResource
              )(WebSocketClient[IO].connectHighLevel(WSRequest(EventWsUri))),
              _ => true.pure[IO]
            ).map(_.some)
        clientConfigPot  <- useStateView(Pot.pending[ClientConfig])
        rootModelData    <- useStateView(RootModelData.Initial)
        configApiStatus  <- useStateView(ApiStatus.Idle)
        _                <-
          useEffectStreamWhenDepsReady(wsConnection.flatMap(_.toPot)):
            _.receiveStream // Setup server event processor (2)
              .through(parseClientEvents)
              .evalMap:
                case Right(event) =>
                  processStreamEvent(
                    clientConfigPot.async.mod,
                    rootModelData.async.mod,
                    syncStatus.async.mod,
                    configApiStatus.async.mod,
                    toastRef
                  )(event)
                case Left(error)  =>
                  processStreamError(rootModelData.async.mod)(error)
        ctxPot           <- useState(Pot.pending[AppContext[IO]])
        _                <-
          useAsyncEffectWhenDepsReady(clientConfigPot.get) { clientConfig => // Build AppContext (4)
            val ctxResource: Resource[IO, AppContext[IO]] =
              (for
                dispatcher                                 <- Dispatcher.parallel[IO]
                given WebSocketJsBackend[IO]                = WebSocketJsBackend[IO](dispatcher)
                given WebSocketJsClient[IO, ObservationDB] <-
                  Resource.eval:
                    WebSocketJsClient.of[IO, ObservationDB](
                      clientConfig.odbUri.toString,
                      "ODB",
                      reconnectionStrategy
                    )
              yield AppContext[IO](
                AppContext.version(clientConfig.environment),
                SSOClient(SSOConfig(clientConfig.ssoUri)),
                plainFetchClient,
                (tab: AppTab) => MainApp.routerCtl.urlFor(tab.getPage).value,
                (tab: AppTab, via: SetRouteVia) => MainApp.routerCtl.set(tab.getPage, via),
                toastRef
              )).onError:
                case t => Resource.eval(IO(t.printStackTrace()))

            ctxResource.allocated.flatMap: (ctx, release) =>
              ctxPot.setStateAsync(ctx.ready).as(release) // Return `release` as cleanup effect
          }
        _                <-
          useEffectWhenDepsReady(ctxPot.value): ctx =>
            // Once AppContext is ready, proceed to attempt login (5)
            enforceStaffRole(ctx.ssoClient).attempt.flatMap: userVault =>
              rootModelData.async.mod(_.withLoginResult(userVault.map(_.some)))
        authHeaderRef    <-
          useShadowRef:
            rootModelData.get.userVault.toOption.flatten.map(_.authorizationHeader)
        _                <-
          useAsyncEffectWhenDepsReady(
            (ctxPot.value, rootModelData.get.userVault.map(_.toPot).flatten.void).tupled
          ): (ctx, _) =>              // Initialize ODB client (6)
            ctx
              .initODBClient:
                authHeaderRef.getAsync.map:
                  _.map: authHeader =>
                    Map(Authorization.name.toString -> authHeader.credentials.renderString.asJson)
                  .getOrElse(Map.empty)
              .as(ctx.closeODBClient) // Disconnect on logout
        // Subscribe to client event stream (and initialize ClientConfig)
        apiClientOpt     <- useState(none[ApiClient])
        _                <-
          useEffectWhenDepsReady(clientConfigPot.get): clientConfig =>
            apiClientOpt
              .setState:
                ApiClient(
                  fetchClient,
                  ApiBasePath,
                  clientConfig.clientId,
                  authHeaderRef.getAsync,
                  t =>
                    toastRef
                      .show:
                        MessageItem(
                          id = "configApiError",
                          content = "Error saving changes",
                          severity = Message.Severity.Error
                        )
                      .to[IO] >>
                      LogMessage
                        .now(ObserveLogLevel.Error, t.getMessage)
                        .flatMap: logMsg =>
                          rootModelData.async
                            .zoom(RootModelData.globalLog)
                            .mod(_.append(logMsg))
                      >>
                      Logger[IO].error(t.getMessage) >>
                      syncStatus.async.set(SyncStatus.OutOfSync.some) // Triggers reSync
                ).some
        _                <-
          // If SyncStatus goes OutOfSync, start reSync (or cancel if it goes back to Synced)
          useEffectWithDeps(syncStatus.get):
            case Some(SyncStatus.OutOfSync) =>
              apiClientOpt.value
                .map: client =>
                  singleDispatcher.submit(client.refresh)
                .orEmpty
            case Some(SyncStatus.Synced)    => singleDispatcher.cancel
            case _                          => IO.unit
        odbStatus        <-
          useStream(ctxPot.value.void): _ =>
            ctxPot.value
              .map(_.odbClient)
              .toOption
              .foldMap(_.statusStream) // Track ODB initialization status
        subscribed       <- useRef(false)
        _                <-
          useEffectStreamResourceWhenDepsReady(
            (clientConfigPot.get,
             ctxPot.value,
             odbStatus.toPot.filter(_ === PersistentClientStatus.Connected)
            ).tupled
          ): (clientConfig, ctx, _) => // Query ready observations for the site (7)
            import ctx.given

            Option
              .unless(subscribed.value) {
                val readyObservations: ViewF[IO, Pot[List[ObsSummary]]] =
                  rootModelData
                    .zoom(RootModelData.readyObservations)
                    .async

                Resource.pure(fs2.Stream.eval[IO, Unit](subscribed.setAsync(true))) >>
                  Resource
                    .eval(IO.realTime)
                    .flatMap: now =>
                      val localObservingNight: LocalObservingNight =
                        LocalObservingNight
                          .fromSiteAndInstant(clientConfig.site, Instant.ofEpochMilli(now.toMillis))

                      ObsQueriesGQL
                        .ActiveObservationIdsQuery[IO]
                        .query(
                          clientConfig.site,
                          localObservingNight.toLocalDate
                        )
                        .raiseGraphQLErrors
                        .flatMap: data =>
                          readyObservations.set:
                            data.observationsByWorkflowState.ready
                        .recoverWith(t => readyObservations.set(Pot.error(t)))
                        .void
                        .reRunOnResourceSignals:
                          ObsQueriesGQL.ObservationEditSubscription.subscribe[IO]()
              }
              .orEmpty
        permitPot        <-
          useEffectResultOnMount(Semaphore[IO](1).map(_.permit))
      yield
        val apisOpt: Option[(ConfigApi[IO], SequenceApi[IO], ODBQueryApi[IO])] =
          (ctxPot.value.toOption,
           apiClientOpt.value,
           rootModelData.get.observer,
           permitPot.toOption
          ).mapN: (ctx, client, observer, permit) =>
            import ctx.given

            (
              ConfigApiImpl(client = client, apiStatus = configApiStatus, latch = permit),
              SequenceApiImpl(
                client = client,
                observer = observer,
                requests = rootModelData.zoom(RootModelData.obsRequests)
              ),
              ODBQueryApiImpl(rootModelData.zoom(RootModelData.nighttimeObservation).async)
            )

        def provideApiCtx(children: VdomNode*) =
          apisOpt.fold(React.Fragment(children*)): (configApi, sequenceApi, odbQueryApi) =>
            ConfigApi.ctx.provide(configApi)(
              SequenceApi.ctx.provide(sequenceApi)(
                ODBQueryApi.ctx.provide(odbQueryApi)(
                  children*
                )
              )
            )

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

        val nighttimeObservationSequenceState: SequenceState =
          rootModelData.get.nighttimeObservation
            .map(_.obsId)
            .flatMap(rootModelData.get.executionState.get)
            .map(_.sequenceState)
            .getOrElse(SequenceState.Idle)

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
                  ObservationSyncer(
                    rootModelData.zoom(RootModelData.nighttimeObservation),
                    nighttimeObservationSequenceState
                  ),
                  router(RootModel(clientConfigPot.get, rootModelData))
                )
              )
            )
    }

  inline def apply() = component()
