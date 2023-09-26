// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import java.io.FileInputStream
import java.nio.file.{Path => FilePath}
import java.security.KeyStore
import java.security.Security
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import scala.concurrent.duration.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import fs2.Stream
import cats.effect.std.{Dispatcher, Queue}
import fs2.concurrent.Topic
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.server.Router
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.Server
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.middleware.Metrics
import org.http4s.server.middleware.{Logger => Http4sLogger}
import pureconfig.*
import observe.model.config.*
import observe.model.events.*
import observe.server
//import observe.server.CaServiceInit
import observe.server.ObserveEngine
import observe.server.ObserveFailure
import observe.server.Systems
// import observe.server.executeEngine
//import observe.server.tcs.GuideConfigDb
import observe.web.server.OcsBuildInfo
import observe.web.server.config.*
// import observe.web.server.logging.AppenderForClients
// import observe.web.server.security.AuthenticationService
import web.server.common.LogInitialization
import web.server.common.RedirectToHttpsRoutes
import cats.effect.{Ref, Resource, Temporal}
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.blaze.server.BlazeServerBuilder
import com.comcast.ip4s.Dns
import fs2.compression.Compression
import cats.Monad
import observe.server.CaServiceInit

object WebServerLauncher extends IOApp with LogInitialization {
  // private given L: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("observe")

  // Attempt to get the configuration file relative to the base dir
  def configurationFile[F[_]: Sync]: F[FilePath] =
    baseDir[F].map(_.resolve("conf").resolve("app.conf"))

  // Try to load config from the file and fall back to the common one in the class path
  def config[F[_]: Sync]: F[ConfigObjectSource] = {
    val defaultConfig = ConfigSource.resources("app.conf").pure[F]
    val fileConfig    = configurationFile.map(ConfigSource.file)

    // ConfigSource, first attempt the file or default to the classpath file
    (fileConfig, defaultConfig).mapN(_.optional.withFallback(_))
  }

  /** Configures the Authentication service */
  // def authService[F[_]: Sync: Logger](
  //   mode: Mode,
  //   conf: AuthenticationConfig
  // ): F[AuthenticationService[F]] =
  //   Sync[F].delay(AuthenticationService[F](mode, conf))

  def makeContext[F[_]: Async](tls: TLSConfig): F[SSLContext] = Sync[F].delay {
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

  /** Resource that yields the running web server */
//   def webServer[F[_]: Logger: Async](
//     conf:      ObserveConfiguration,
//     // as:        AuthenticationService[F],
//     // inputs:    server.EventQueue[F],
//     outputs:   Topic[F, ObserveEvent],
//     se:        ObserveEngine[F],
//     clientsDb: ClientsSetDb[F]
//   ): Resource[F, Server] = {
//
//     val ssl: F[Option[SSLContext]] = conf.webServer.tls.map(makeContext[F]).sequence
//
//     def build(all: WebSocketBuilder2[F] => HttpRoutes[F]): Resource[F, Server] =
//       Resource.eval {
//         val builder =
//           BlazeServerBuilder[F]
//             .bindHttp(conf.webServer.port, conf.webServer.host)
//             .withHttpWebSocketApp(wsb => (prRouter <+> all(wsb)).orNotFound)
//         ssl.map(_.fold(builder)(builder.withSslContext)).map(_.resource)
//       }.flatten
//
//     def router(wsb: WebSocketBuilder2[F]) = Router[F](
// //       "/api/observe/commands" -> new ObserveCommandRoutes(as, inputs, se).service,
// //       "/api"                  -> new ObserveUIApiRoutes(
// //         conf.site,
// //         conf.mode,
// //         as,
// // //                                       se.systems.guideDb,
// // //                                       se.systems.gpi.statusDb,
// //         clientsDb,
// //         outputs,
// //         wsb
// //       ).service
// //      "/api/observe/guide"    -> new GuideConfigDbRoutes(se.systems.guideDb).service,
//       // "/smartgcal"            -> new SmartGcalRoutes[F](cal).service
//     )
//
//     // val pingRouter = Router[F](
//     //   "/ping" -> new PingRoutes(as).service
//     // )
//     //
//     // def loggedRoutes(wsb: WebSocketBuilder2[F])                               =
//     //   pingRouter <+> Http4sLogger.httpRoutes(logHeaders = false, logBody = false)(router(wsb))
//     // val metricsMiddleware: Resource[F, WebSocketBuilder2[F] => HttpRoutes[F]] =
//     //   Prometheus.metricsOps[F](cr, "observe").map(x => wsb => Metrics[F](x)(loggedRoutes(wsb)))
//     //
//     // loggedRoutes.flatMap(x => build(x))
//
//   }

  def redirectWebServer[F[_]: Logger: Async](
//    gcdb: GuideConfigDb[F],
    // cal: SmartGcal
  )(conf: WebServerConfiguration): Resource[F, Server] = {
    val router = Router[F](
//      "/api/observe/guide" -> new GuideConfigDbRoutes(gcdb).service,
      // "/smartgcal" -> new SmartGcalRoutes[F](cal).service,
      "/" -> new RedirectToHttpsRoutes[F](443, conf.externalBaseUrl).service
    )

    BlazeServerBuilder[F]
      .bindHttp(conf.insecurePort, conf.host)
      .withHttpApp(router.orNotFound)
      .resource
  }

  def printBanner[F[_]: Logger](conf: ObserveConfiguration): F[Unit] = {
    val banner = """
   ____  __
  / __ \/ /_  ________  ______   _____
 / / / / __ \/ ___/ _ \/ ___/ | / / _ \
/ /_/ / /_/ (__  )  __/ /   | |/ /  __/
\____/_.___/____/\___/_/    |___/\___/

"""
    val msg    =
      s"""Start web server for site ${conf.site} on ${conf.mode} mode, version ${OcsBuildInfo.version}
"""
    val goTo   = s"Go to https://${conf.webServer.host}:${conf.webServer.port}/"
    Logger[F].info(banner + msg + goTo)
  }

  // We need to manually update the configuration of the logging subsystem
  // to support capturing log messages and forward them to the clients
  def logToClients(
    out:        Topic[IO, ObserveEvent],
    dispatcher: Dispatcher[IO]
  ): IO[Appender[ILoggingEvent]] = IO.apply {
    import ch.qos.logback.classic.{AsyncAppender, Logger, LoggerContext}
    import org.slf4j.LoggerFactory

    val asyncAppender = new AsyncAppender
    // val appender      = new AppenderForClients(out)(dispatcher)
    Option(LoggerFactory.getILoggerFactory)
      .collect { case lc: LoggerContext =>
        lc
      }
      .foreach { ctx =>
        asyncAppender.setContext(ctx)
        // appender.setContext(ctx)
        // asyncAppender.addAppender(appender)
      }

    Option(LoggerFactory.getLogger("observe"))
      .collect { case l: Logger =>
        l
      }
      .foreach { l =>
        l.addAppender(asyncAppender)
        asyncAppender.start()
        // appender.start()
      }
    asyncAppender
  }

  // Logger of error of last resort.
  // def logError[F[_]: Logger]: PartialFunction[Throwable, F[Unit]] = {
  //   case e: ObserveFailure =>
  //     Logger[F].error(e)(s"Observe global error handler ${ObserveFailure.explain(e)}")
  //   case e: Exception      => Logger[F].error(e)("Observe global error handler")
  // }

  /** Reads the configuration and launches the observe engine and web server */
  def observe: IO[ExitCode] = {

    // Override the default client config
    def mkClient(timeout: FiniteDuration): IO[Client[IO]] =
      JdkHttpClient.simple[IO].map(c => Client(r => c.run(r).timeout(timeout)))

    def engineIO(
      conf:       ObserveConfiguration,
      httpClient: Client[IO]
    )(using Logger[IO]): Resource[IO, ObserveEngine[IO]] =
      for {
        caS  <- Resource.eval(CaServiceInit.caInit[IO](conf.observeEngine))
        sys  <- Systems.build(conf.site, httpClient, conf.observeEngine, conf.lucumaSSO, caS)
        seqE <- Resource.eval(ObserveEngine.build(conf.site, sys, conf.observeEngine))
      } yield seqE

//     def webServerIO(
//       conf: ObserveConfiguration,
//       out:  Topic[IO, ObserveEvent],
//       en:   ObserveEngine[IO],
//       cs:   ClientsSetDb[IO]
//     ): Resource[IO, Unit] =
//       for {
//         // as <- Resource.eval(authService[IO](conf.mode, conf.authentication))
//         // ca <- Resource.eval(SmartGcalInitializer.init[IO](conf.smartGcal))
// //        _  <- redirectWebServer(en.systems.guideDb, ca)(conf.webServer)
//         _ <- webServer[IO](conf, out, en, cr, cs)
//       } yield ()

    def publishStats[F[_]: Temporal](cs: ClientsSetDb[F]): Stream[F, Unit] =
      Stream.fixedRate[F](10.minute).flatMap(_ => Stream.eval(cs.report))

    val observe: Resource[IO, ExitCode] =
      for {
        given Logger[IO] <-
          Resource.eval(setupLogger[IO]) // Initialize log before the engine is setup
        conf             <- Resource.eval(config[IO].flatMap(loadConfiguration[IO]))
        _                <- Resource.eval(printBanner(conf))
        cli              <- Resource.eval(mkClient(conf.observeEngine.dhsTimeout))
        // // inq    <- Resource.eval(Queue.bounded[IO, executeEngine.EventType](10))
        out              <- Resource.eval(Topic[IO, ObserveEvent])
        dsp              <- Dispatcher.parallel[IO]
        _                <- Resource.eval(logToClients(out, dsp))
        cs               <- Resource.eval(
                              Ref.of[IO, ClientsSetDb.ClientsSet](Map.empty).map(ClientsSetDb.apply[IO](_))
                            )
        _                <- Resource.eval(publishStats(cs).compile.drain.start)
        engine           <- engineIO(conf, cli)
        // _      <- webServerIO(conf, inq, out, engine, cs)
        //   _      <- Resource.eval(
        //               inq.size
        //                 .map(l => Logger[IO].debug(s"Queue length: $l").whenA(l > 1))
        //                 .start
        //             )
        //   _      <- Resource.eval(
        //               out.subscribers
        //                 .evalMap(l => Logger[IO].debug(s"Subscribers amount: $l").whenA(l > 1))
        //                 .compile
        //                 .drain
        //                 .start
        //             )
        //   f      <- Resource.eval(
        //               engine.eventStream(inq).through(out.publish).compile.drain.onError(logError).start
        //             )
        //   _      <- Resource.eval(f.join)        // We need to join to catch uncaught errors
        _                <- Resource.eval(IO.println(s"Observe started $engine"))
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
