// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.data.OptionT
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.Temporal
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
import lucuma.core.model.User
import lucuma.sso.client.SsoClient
import lucuma.sso.client.SsoJwtReader
import lucuma.sso.client.util.JwtDecoder
import observe.model.ClientId
import observe.model.config.*
import observe.model.events.*
import observe.model.events.client.ClientEvent
import observe.server
import observe.server.CaServiceInit
import observe.server.ObserveEngine
import observe.server.Systems
import observe.web.server.OcsBuildInfo
import observe.web.server.config.*
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.server.Router
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.Server
import org.http4s.server.middleware.{Logger => Http4sLogger}
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger
import pureconfig.*
import web.server.common.LogInitialization
import web.server.common.RedirectToHttpsRoutes

import java.io.FileInputStream
import java.nio.file.{Path => JavaPath}
import java.nio.file.{Files => JavaFiles}
import java.security.KeyStore
import java.security.Security
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import scala.concurrent.duration.*

object WebServerLauncher extends IOApp with LogInitialization {

  // Try to load configs for deployment and staging and fall back to the common one in the class path
  private def config[F[_]: Sync: Logger]: F[ConfigObjectSource] =
    for
      confDir <- baseDir[F].map(_.resolve("conf"))
      deploy   = confDir.resolve("local").resolve("app.conf")
      staging  = confDir.resolve("app.conf")
      _       <- Logger[F].info("Loading configuration:")
      _       <- Logger[F].info(s" - $deploy (present: ${JavaFiles.exists(deploy)}), with fallback:")
      _       <- Logger[F].info(s" - $staging (present: ${JavaFiles.exists(staging)}), with fallback:")
      _       <- Logger[F].info(s" - <resources>/app.conf")
    yield ConfigSource
      .file(deploy)
      .optional
      .withFallback:
        ConfigSource.file(staging).optional.withFallback(ConfigSource.resources("app.conf"))

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

  private def ssoClient[F[_]: Async: Network: Logger](
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
  private def webServer[F[_]: Logger: Async: Files: Network: Compression](
    conf:      ObserveConfiguration,
    clientsDb: ClientsSetDb[F],
    ssoClient: SsoClient[F, User],
    oe:        ObserveEngine[F]
  ): Resource[F, Server] = {

    def router(
      wsb:    WebSocketBuilder2[F],
      events: Topic[F, (Option[ClientId], ClientEvent)]
    ) = Router[F](
      "/"                   -> StaticRoutes().service,
      "/api/observe/guide"  -> GuideConfigDbRoutes(oe.systems.guideDb).service,
      "/api/observe"        -> ObserveCommandRoutes(ssoClient, oe).service,
      "/api/observe/ping"   -> PingRoutes(ssoClient).service,
      "/api/observe/events" -> ObserveEventRoutes(
        conf.site,
        conf.environment,
        conf.observeEngine.odb,
        conf.lucumaSSO.ssoUrl,
        clientsDb,
        oe,
        events,
        wsb
      ).service
    )

    def builder(events: Topic[F, (Option[ClientId], ClientEvent)]) =
      EmberServerBuilder
        .default[F]
        .withHost(conf.webServer.host)
        .withPort(conf.webServer.port)
        .withHttpWebSocketApp(wsb =>
          Http4sLogger
            .httpRoutes(logHeaders = false, logBody = false)(router(wsb, events))
            .orNotFound
        )

    def builderWithTLS(events: Topic[F, (Option[ClientId], ClientEvent)]) =
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
      _      <- oe.eventStream
                  .evalMapFilter(_.toClientEvent.traverse(wst.publish1))
                  .compile
                  .drain
                  .background
      server <- builderWithTLS(wst)
    } yield server

  }

  private def redirectWebServer[F[_]: Logger: Async: Network](
    conf: WebServerConfiguration
  ): Resource[F, Server] = {
    val router = Router[F](
      "/" -> new RedirectToHttpsRoutes[F](443, conf.externalBaseUrl).service
    )

    EmberServerBuilder
      .default[F]
      .withHost(conf.host)
      .withPort(conf.insecurePort)
      .withHttpApp(router.orNotFound)
      .build
  }

  private def printBanner[F[_]: Logger](conf: ObserveConfiguration): F[Unit] = {
    val banner = """
   ____  __
  / __ \/ /_  ________  ______   _____
 / / / / __ \/ ___/ _ \/ ___/ | / / _ \
/ /_/ / /_/ (__  )  __/ /   | |/ /  __/
\____/_.___/____/\___/_/    |___/\___/

"""
    val msg    =
      s"""
      | Start web server for site ${conf.site} on ${conf.environment} environment, version ${OcsBuildInfo.version}
      | Connected to odb at ${conf.observeEngine.odb}
      |
      | Go to https://${conf.webServer.host}:${conf.webServer.port}
      |"""
    Logger[F].info(banner + msg)
  }

  // Override the default client config
  private def mkClient(timeout: FiniteDuration): IO[Client[IO]] =
    JdkHttpClient.simple[IO].map(c => Client(r => c.run(r).timeout(timeout)))

  private def engineIO(
    conf:       ObserveConfiguration,
    httpClient: Client[IO]
  )(using Logger[IO]): Resource[IO, ObserveEngine[IO]] =
    for {
      caS  <- Resource.eval(CaServiceInit.caInit[IO](conf.observeEngine))
      sys  <- Systems.build(conf.site, httpClient, conf.observeEngine, conf.lucumaSSO, caS)
      seqE <- Resource.eval(ObserveEngine.build(conf.site, sys, conf.observeEngine))
    } yield seqE

  private def publishStats[F[_]: Temporal](cs: ClientsSetDb[F]): Stream[F, Unit] =
    Stream.fixedRate[F](10.minute).flatMap(_ => Stream.eval(cs.report))

  /** Reads the configuration and launches the observe engine and web server */
  def observe: IO[ExitCode] = {

    val observe: Resource[IO, ExitCode] =
      for {
        given Logger[IO] <-
          Resource.eval(setupLogger[IO]) // Initialize log before the engine is setup
        conf             <- Resource.eval(config[IO].flatMap(loadConfiguration[IO]))
        _                <- Resource.eval(printBanner(conf))
        cli              <- Resource.eval(mkClient(conf.observeEngine.dhsTimeout))
        out              <- Resource.eval(Topic[IO, ObserveEvent])
        cs               <- Resource.eval(
                              Ref.of[IO, ClientsSetDb.ClientsSet](Map.empty).map(ClientsSetDb.apply[IO](_))
                            )
        _                <- Resource.eval(publishStats(cs).compile.drain.start)
        engine           <- engineIO(conf, cli)
        _                <- redirectWebServer(conf.webServer)
        sso              <- ssoClient(cli, conf.lucumaSSO)
        _                <- webServer(conf, cs, sso, engine)
      } yield ExitCode.Success

    observe.use(_ => IO.never)

  }

  /** Reads the configuration and launches the observe */
  override def run(args: List[String]): IO[ExitCode] =
    observe.guaranteeCase {
      case Outcome.Errored(e) =>
        IO.println(s"Observe exited with error $e")
      case _                  => IO.unit
    }

}
