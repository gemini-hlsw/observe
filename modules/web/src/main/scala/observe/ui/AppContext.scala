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
import eu.timepit.refined.types.string.NonEmptyString

case class AppContext[F[_]: FlatMap](version: NonEmptyString, ssoClient: SSOClient[F])(using
  val logger:    Logger[F],
  val odbClient: WebSocketJSClient[F, ObservationDB]
):
  def initODBClient(payload: Map[String, Json]): F[Unit] =
    odbClient.connect() >> odbClient.initialize(payload)

  val closeODBClient: F[Unit] =
    odbClient.terminate() >> odbClient.disconnect(CloseParams(code = 1000))

object AppContext:
  val ctx: Context[AppContext[IO]] = React.createContext(null) // No default value

  import lucuma.ui.utils.versionDateTimeFormatter
  import lucuma.ui.utils.versionDateFormatter
  import lucuma.ui.enums.ExecutionEnvironment
  import java.time.Instant

  val gitHash = BuildInfo.gitHeadCommit

  def version(environment: ExecutionEnvironment): NonEmptyString = {
    val instant = Instant.ofEpochMilli(BuildInfo.buildDateTime)
    NonEmptyString.unsafeFrom(
      (environment match
        case ExecutionEnvironment.Development =>
          versionDateTimeFormatter.format(instant)
        case _                                =>
          versionDateFormatter.format(instant) +
            "-" + gitHash.map(_.take(7)).getOrElse("NONE")
      )
        + environment.suffix
          .map(suffix => s"-$suffix")
          .orEmpty
    )
  }
