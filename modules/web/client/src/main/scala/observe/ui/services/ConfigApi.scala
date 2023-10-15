// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.MonadThrow
import cats.effect.IO
import japgolly.scalajs.react.React
import japgolly.scalajs.react.feature.Context
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor

trait ConfigApi[F[_]: MonadThrow]:
  def setImageQuality(iq:    ImageQuality): F[Unit]    = NotAuthorized
  def setCloudExtinction(ce: CloudExtinction): F[Unit] = NotAuthorized
  def setWaterVapor(wv:      WaterVapor): F[Unit]      = NotAuthorized
  def setSkyBackground(sb:   SkyBackground): F[Unit]   = NotAuthorized
  def refresh: F[Unit] = NotAuthorized

  def isBlocked: Boolean = false

object ConfigApi:
  // Default value is NotAuthorized implementations
  val ctx: Context[ConfigApi[IO]] = React.createContext(new ConfigApi[IO] {})
