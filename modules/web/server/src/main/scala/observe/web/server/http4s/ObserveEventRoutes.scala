// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import java.util.UUID

import scala.concurrent.duration.*

import cats.effect.Async
import cats.effect.Sync
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import fs2.compression.Compression
import fs2.concurrent.Topic
import io.circe.Encoder
import io.circe.syntax.*
import org.typelevel.log4cats.Logger
// import lucuma.core.enums.GiapiStatus
import org.http4s.*
import org.http4s.dsl.*
import org.http4s.headers.`User-Agent`
import org.http4s.server.middleware.GZip
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import org.http4s.websocket.WebSocketFrame.Close
import org.http4s.websocket.WebSocketFrame.Ping
import org.http4s.websocket.WebSocketFrame.Pong
import observe.model.ClientId
import observe.model.*
import observe.model.events.*
import observe.server.ObserveEngine
import observe.model.events.client.ObserveClientEvent

/**
 * Rest Endpoints under the /api route
 */
class ObserveEventRoutes[F[_]: Async: Compression](
  clientsDb:        ClientsSetDb[F],
  engineOutput:     Topic[F, ObserveClientEvent],
  webSocketBuilder: WebSocketBuilder2[F]
)(using
  L:                Logger[F]
) extends ModelLenses
    with Http4sDsl[F] {

  val pingInterval: FiniteDuration = 10.second

  /**
   * Creates a process that sends a ping every second to keep the connection alive
   */
  private def pingStream: Stream[F, Ping] =
    Stream.fixedRate[F](pingInterval).flatMap(_ => Stream.emit(Ping()))

  val protectedServices: HttpRoutes[F] =
    HttpRoutes.of { case ws @ GET -> Root / "api" / "observe" / "events" =>
      // If the user didn't login, anonymize
      // val anonymizeF: ObserveEvent => ObserveEvent = user.fold(_ => anonymize, _ => identity)
      //
      // def initialEvent(clientId: ClientId): Stream[F, WebSocketFrame] =
      //   Stream.emit(toFrame(ConnectionOpenEvent(user.toOption, clientId, OcsBuildInfo.version)))

      def engineEvents(clientId: ClientId): Stream[F, WebSocketFrame] =
        engineOutput
          .subscribe(100)
          // .map(anonymizeF)
          // .filter(filterOutNull)
          // .filter(filterOutOnClientId(clientId))
          .map(toFrame)
      val clientSocket                                                = (ws.remoteAddr, ws.remotePort).mapN((a, p) => s"$a:$p").orEmpty
      val userAgent                                                   = ws.headers.get[`User-Agent`]

      // We don't care about messages sent over ws by clients but we want to monitor
      // control frames and track that pings arrive from clients
      def clientEventsSink(clientId: ClientId): Pipe[F, WebSocketFrame, Unit] =
        _.flatTap {
          case Close(_) =>
            Stream.eval(
              clientsDb.removeClient(clientId) *> L.debug(s"Closed client $clientSocket")
            )
          case Pong(_)  => Stream.eval(L.trace(s"Pong from $clientSocket"))
          case _        => Stream.empty
        }.filter {
          case Pong(_) => true
          case _       => false
        }.void
          .through(
            ObserveEngine.failIfNoEmitsWithin(5 * pingInterval, s"Lost ping on $clientSocket")
          )

      // Create a client specific websocket
      for {
        clientId <- Sync[F].delay(ClientId(UUID.randomUUID()))
        _        <- clientsDb.newClient(clientId, clientSocket, userAgent)
        _        <- L.info(s"New client $clientSocket => ${clientId.self}")
        // initial   = initialEvent(clientId)
        streams   = Stream(pingStream, engineEvents(clientId)).parJoinUnbounded
                      .onFinalize[F](clientsDb.removeClient(clientId))
        ws       <- webSocketBuilder
                      .withFilterPingPongs(false)
                      .build(streams, clientEventsSink(clientId))
      } yield ws

    }

  def service: HttpRoutes[F] =
    GZip(protectedServices)

  // Event to WebSocket frame
  private def toFrame[A: Encoder](e: A) =
    Text(e.asJson.noSpaces)

  // Stream observe events to clients and a ping
  // private def anonymize(e: ObserveEvent) =
  //   // Hide the name and target name for anonymous users
  //   telescopeTargetNameT
  //     .replace("*****")
  //     .andThen(observeTargetNameT.replace("*****"))
  //     .andThen(sequenceNameT.replace(""))(e)
  //
  // // Filter out NullEvents from the engine
  // private def filterOutNull =
  //   (e: ObserveEvent) =>
  //     e match {
  //       case NullEvent => false
  //       case _         => true
  //     }
  //
  // // Messages with a clientId are only sent to the matching client
  // private def filterOutOnClientId(clientId: ClientId) =
  //   (e: ObserveEvent) =>
  //     e match {
  //       case e: ForClient if e.clientId =!= clientId => false
  //       case _                                       => true
  //     }
  //
}
