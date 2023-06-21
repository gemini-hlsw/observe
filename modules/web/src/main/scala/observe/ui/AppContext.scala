// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import cats.effect.IO
import japgolly.scalajs.react.React
import japgolly.scalajs.react.feature.Context
import org.typelevel.log4cats.Logger
import clue.js.WebSocketJSClient
import lucuma.schemas.ObservationDB

final case class AppContext[F[_]](
  logger:    Logger[F],
  odbClient: WebSocketJSClient[F, ObservationDB]
):
  given Logger[F]                           = logger
  given WebSocketJSClient[F, ObservationDB] = odbClient

object AppContext:
  val ctx: Context[AppContext[IO]] = React.createContext(null) // No default value
