// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import cats.FlatMap
import cats.effect.IO
import cats.syntax.all.*
import clue.js.WebSocketJSClient
import clue.websocket.CloseParams
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Json
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.React
import japgolly.scalajs.react.extra.router.SetRouteVia
import japgolly.scalajs.react.feature.Context
import lucuma.schemas.ObservationDB
import lucuma.ui.enums.ExecutionEnvironment
import lucuma.ui.sso.SSOClient
import lucuma.ui.utils.versionDateFormatter
import lucuma.ui.utils.versionDateTimeFormatter
import observe.ui.model.enums.AppTab
import observe.ui.services.ConfigApi
import org.typelevel.log4cats.Logger

import java.time.Instant

case class AppContext[F[_]: FlatMap](
  version:       NonEmptyString,
  ssoClient:     SSOClient[F],
  configApi:     ConfigApi[F],
  pageUrl:       AppTab => String,
  setPageVia:    (AppTab, SetRouteVia) => Callback
)(using
  val logger:    Logger[F],
  val odbClient: WebSocketJSClient[F, ObservationDB]
):
  def initODBClient(payload: Map[String, Json]): F[Unit] =
    odbClient.connect() >> odbClient.initialize(payload)

  val closeODBClient: F[Unit] =
    odbClient.terminate() >> odbClient.disconnect(CloseParams(code = 1000))

  def pushPage(appTab: AppTab): Callback = setPageVia(appTab, SetRouteVia.HistoryPush)

  def replacePage(appTab: AppTab): Callback = setPageVia(appTab, SetRouteVia.HistoryReplace)

object AppContext:
  val ctx: Context[AppContext[IO]] = React.createContext(null) // No default value

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
