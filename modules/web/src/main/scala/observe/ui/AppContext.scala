// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import cats.FlatMap
import cats.effect.IO
import cats.syntax.all.*
import clue.js.WebSocketJSClient
import clue.websocket.CloseParams
import io.circe.Json
import japgolly.scalajs.react.React
import japgolly.scalajs.react.feature.Context
import lucuma.schemas.ObservationDB
import lucuma.ui.sso.SSOClient
import org.typelevel.log4cats.Logger

case class AppContext[F[_]: FlatMap](ssoClient: SSOClient[F])(using
  val logger:    Logger[F],
  val odbClient: WebSocketJSClient[F, ObservationDB]
):
  def initODBClient(payload: Map[String, Json]): F[Unit] =
    odbClient.connect() >> odbClient.initialize(payload)

  val closeODBClient: F[Unit] =
    odbClient.terminate() >> odbClient.disconnect(CloseParams(code = 1000))

object AppContext:
  val ctx: Context[AppContext[IO]] = React.createContext(null) // No default value
