// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.Stream
import fs2.compression.Compression
import fs2.concurrent.Topic
import fs2.io.file.Files
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext
import fs2.io.net.tls.TLSParameters
import lucuma.core.enums.ExecutionEnvironment
import lucuma.core.enums.Site
import lucuma.core.model.User
import lucuma.sso.client.SsoClient
import lucuma.sso.client.SsoJwtReader
import lucuma.sso.client.util.JwtDecoder
import natchez.EntryPoint
import natchez.Trace
import natchez.honeycomb.Honeycomb
import natchez.http4s.NatchezMiddleware
import observe.model.ClientId
import observe.model.config.*
import observe.model.events.*
import observe.server.CaServiceInit
import observe.server.ObserveEngine
import observe.server.Systems
import observe.server.tcs.GuideConfigDb
import observe.web.server.OcsBuildInfo
import observe.web.server.config.*
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.Server
import org.http4s.server.middleware.Logger as Http4sLogger
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger
import pureconfig.*

import java.io.FileInputStream
import java.nio.file.Files as JavaFiles
import java.security.KeyStore
import java.security.Security
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import scala.concurrent.duration.*

object WebServerLauncher extends IOApp with LogInitialization {
  val ServiceName: String = "observe"

  // Try to load configs for deployment and staging and fall back to the common one in the class path
  private def config[F[_]: Sync: Logger]: F[ConfigObjectSource] =
    for
      confDir    <- baseDir[F].map(_.resolve("conf"))
      secretsConf = confDir.resolve("local").resolve("secrets.conf")
      systemsConf = confDir.resolve("local").resolve("systems.conf")
      site        = sys.env.get("SITE").getOrElse(sys.error("SITE environment variable not set"))
      siteConf    = confDir.resolve(site).resolve("site.conf")
      _          <- Logger[F].info("Loading configuration:")
      _          <- Logger[F].info:
                      s" - $systemsConf (present: ${JavaFiles.exists(systemsConf)}), with fallback:"
      _          <- Logger[F].info:
                      s" - $secretsConf (present: ${JavaFiles.exists(secretsConf)}), with fallback:"
      _          <- Logger[F].info(s" - $siteConf (present: ${JavaFiles.exists(siteConf)}), with fallback:")
      _          <- Logger[F].info(s" - <resources>/base.conf")
    yield ConfigSource
      .file(systemsConf)
      .optional
      .withFallback:
        ConfigSource
          .file(secretsConf)
          .optional
          .withFallback:
            ConfigSource.file(siteConf).optional.withFallback(ConfigSource.resources("base.conf"))

  private def makeContext[F[_]: Sync](tls: TLSConfig): F[SSLContext] = Sync[F].delay {
    val ksStream   = new FileInputStream(tls.keyStore.toFile.getAbsolutePath)
    val ks         = KeyStore.getInstance("JKS")
    ks.load(ksStream, tls.keyStorePwd.toCharArray)
    ksStream.close()
    val trustStore = StoreInfo(tls.keyStore.toFile.getAbsolutePath, tls.keyStorePwd)

    val tmf = {
      val ksStream = new FileInputStream(trustStore.path)

      val ks = KeyStore.getInstance("JKS")
      ks.load(ksStream, tls.keyStorePwd.toCharArray)
      ksStream.close()

      val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

      tmf.init(ks)
      tmf.getTrustManagers
    }

    val kmf = KeyManagerFactory.getInstance(
      Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
        .getOrElse(KeyManagerFactory.getDefaultAlgorithm)
    )

    kmf.init(ks, tls.certPwd.toCharArray)

    val context = SSLContext.getInstance("TLS")
    context.init(kmf.getKeyManagers, tmf, null)
    context
  }

  private def tlsContext[F[_]: Sync: Network](tls: TLSConfig): F[Option[TLSContext[F]]] =
    (for {
      ssl <- OptionT.liftF(makeContext[F](tls))
    } yield Network[F].tlsContext.fromSSLContext(ssl)).value

  private def jwtReader[F[_]: Concurrent](sso: LucumaSSOConfiguration): SsoJwtReader[F] =
    SsoJwtReader(JwtDecoder.withPublicKey(sso.publicKey))

  private def ssoClient[F[_]: Async: Logger](
    httpClient: Client[F],
    sso:        LucumaSSOConfiguration
  ): Resource[F, SsoClient[F, User]] =
    Resource.eval(
      SsoClient
        .initial(
          serviceJwt = sso.serviceToken,
          ssoRoot = sso.ssoUrl,
          jwtReader = jwtReader[F](sso),
          httpClient = httpClient
        )
        .map(_.map(_.user))
    )

  /** Resource that yields the running web server */
  private def webServer[F[_]: Async: Trace: Logger: Files: Network: Compression](
    conf:      ObserveConfiguration,
    clientsDb: ClientsSetDb[F],
    ssoClient: SsoClient[F, User],
    oe:        ObserveEngine[F]
  ): Resource[F, Server] = {

    def router(
      wsb:    WebSocketBuilder2[F],
      events: Topic[F, (Option[ClientId], ClientEvent)]
    ): HttpRoutes[F] =
      Router[F](
        "/"                   -> StaticRoutes().service,
        "/api/observe/guide"  -> GuideConfigDbRoutes(oe.systems.guideDb).service,
        "/api/observe"        -> ObserveCommandRoutes(ssoClient, oe).service,
        "/api/observe/ping"   -> PingRoutes(ssoClient).service,
        "/api/observe/events" -> ObserveEventRoutes(
          conf.site,
          conf.environment,
          conf.observeEngine.odbWs,
          conf.lucumaSSO.ssoUrl,
          conf.exploreBaseUrl,
          clientsDb,
          oe,
          events,
          wsb
        ).service
      )

    def natchez(routes: HttpRoutes[F]): HttpRoutes[F] =
      NatchezMiddleware.server[F]:
        Kleisli: (req: Request[F]) =>
          OptionT:
            Trace[F].span("http"):
              routes.run(req).value

    def builder(events: Topic[F, (Option[ClientId], ClientEvent)]): EmberServerBuilder[F] =
      EmberServerBuilder
        .default[F]
        .withHost(conf.webServer.host)
        .withPort(conf.webServer.port)
        .withHttpWebSocketApp(wsb =>
          natchez:
            Http4sLogger
              .httpRoutes(logHeaders = false, logBody = false)(router(wsb, events))
          .orNotFound
        )

    def builderWithTLS(events: Topic[F, (Option[ClientId], ClientEvent)]): Resource[F, Server] =
      Resource
        .eval(
          conf.webServer.tls
            .traverse(tlsContext)
            .map(_.flatten)
            .map(_.fold(builder(events))(builder(events).withTLS(_, TLSParameters.Default)))
            .map(_.build)
        )
        .flatten

    for {
      wst    <- Resource.eval(Topic[F, (Option[ClientId], ClientEvent)])
      _      <- oe.clientEventStream
                  .evalMap: targetedClientEvent =>
                    wst.publish1(targetedClientEvent.clientId, targetedClientEvent.event)
                  .compile
                  .drain
                  .background
      server <- builderWithTLS(wst)
    } yield server

  }

  private def redirectWebServer[F[_]: Logger: Async: Network: Compression](
    conf:    WebServerConfiguration,
    guideDb: GuideConfigDb[F]
  ): Resource[F, Server] = {
    val router = Router[F](
      "/api/observe/guide" -> GuideConfigDbRoutes(guideDb).service,
      "/"                  -> new RedirectToHttpsRoutes[F](443, conf.externalBaseUrl).service
    )

    EmberServerBuilder
      .default[F]
      .withHost(conf.host)
      .withPort(conf.insecurePort)
      .withHttpApp(
        Http4sLogger
          .httpRoutes(logHeaders = false, logBody = false)(router)
          .orNotFound
      )
      .build
  }

  private def printBanner[F[_]: Logger](conf: ObserveConfiguration): F[Unit] = {
    val runtime    = Runtime.getRuntime
    val memorySize = java.lang.management.ManagementFactory
      .getOperatingSystemMXBean()
      .asInstanceOf[com.sun.management.OperatingSystemMXBean]
      .getTotalMemorySize()
    val banner     = """
   ____  __
  / __ \/ /_  ________  ______   _____
 / / / / __ \/ ___/ _ \/ ___/ | / / _ \
/ /_/ / /_/ (__  )  __/ /   | |/ /  __/
\____/_.___/____/\___/_/    |___/\___/

"""
    val msg        =
      s"""
      | Start web server for site ${conf.site} on ${conf.environment} environment, version ${OcsBuildInfo.version}
      | Connected to odb by HTTP at ${conf.observeEngine.odbHttp} and by WS at ${conf.observeEngine.odbWs}
      |
      | cores              : ${runtime.availableProcessors()}
      | current JVM memory : ${runtime.totalMemory() / 1024 / 1024} MB
      | maximum JVM memory : ${runtime.maxMemory() / 1024 / 1024} MB
      | total RAM          : ${memorySize / 1024 / 1024} MB
      | java version       : ${System.getProperty("java.version")}
      |
      | Go to https://${conf.webServer.host}:${conf.webServer.port}
      |"""
    Logger[F].info(banner + msg)
  }

  // We build a client with the default retry policy, which will retry GET requests as
  // well as non-GET requests that contain the `Idempotency-Key` header (which we set in `OdbProxy`).
  private def mkClient[F[_]: Async: Network: Logger](
    timeout: FiniteDuration
  ): Resource[F, Client[F]] =
    EmberClientBuilder
      .default[F]
      .withTimeout(timeout)
      .withLogger(Logger[F])
      .build
    // Uncomment the following to log HTTP requests and responses
    // .map:
    //   Http4sLogger(
    //     logHeaders = true,
    //     logBody = true,
    //     logAction = ((s: String) => Logger[F].trace(s)).some
    //   )(_)

  private def engineIO(
    conf:       ObserveConfiguration,
    httpClient: Client[IO]
  )(using Logger[IO], Trace[IO]): Resource[IO, ObserveEngine[IO]] =
    for {
      caS  <- Resource.eval(CaServiceInit.caInit[IO](conf.observeEngine))
      sys  <- Systems.build(conf.site, httpClient, conf.observeEngine, conf.lucumaSSO, caS)
      seqE <- Resource.eval(ObserveEngine.build(conf.site, sys, conf.observeEngine))
    } yield seqE

  private def publishStats[F[_]: Temporal](cs: ClientsSetDb[F]): Stream[F, Unit] =
    Stream.fixedRate[F](10.minute).flatMap(_ => Stream.eval(cs.report))

  private def entryPoint[F[_]: Sync](
    config:               HoneycombConfiguration,
    site:                 Site,
    executionEnvironment: ExecutionEnvironment
  ): Resource[F, EntryPoint[F]] =
    Honeycomb.entryPoint(ServiceName): cb =>
      Sync[F].delay:
        cb.setWriteKey(config.writeKey)
        cb.setDataset(s"$ServiceName-$site-$executionEnvironment")
        cb.build()

  /** Reads the configuration and launches the observe engine and web server */
  def observe: IO[ExitCode] = {

    val observe: Resource[IO, ExitCode] =
      for // Initialize log before the engine is setup
        given Logger[IO] <- Resource.eval(setupLogger[IO])
        conf             <- Resource.eval(config[IO].flatMap(loadConfiguration[IO]))
        ep               <- entryPoint[IO](conf.honeycomb, conf.site, conf.environment)
        given Trace[IO]  <- Resource.eval(Trace.ioTraceForEntryPoint(ep))
        _                <- Resource.eval(printBanner(conf))
        cli              <- mkClient[IO](conf.observeEngine.dhsTimeout)
        cs               <- Resource.eval:
                              Ref.of[IO, ClientsSetDb.ClientsSet](Map.empty).map(ClientsSetDb.apply[IO](_))
        _                <- Resource.eval(publishStats(cs).compile.drain.start)
        engine           <- engineIO(conf, cli)
        _                <- redirectWebServer(conf.webServer, engine.systems.guideDb)
        sso              <- ssoClient(cli, conf.lucumaSSO)
        _                <- webServer(conf, cs, sso, engine)
      yield ExitCode.Success

    observe.useForever

  }

  /** Reads the configuration and launches the observe */
  override def run(args: List[String]): IO[ExitCode] =
    observe.guaranteeCase {
      case Outcome.Errored(e) =>
        IO.println(s"Observe exited with error $e")
      case _                  => IO.unit
    }

}
