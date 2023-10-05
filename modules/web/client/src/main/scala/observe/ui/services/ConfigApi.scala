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
import observe.model.ClientId

trait ConfigApi[F[_]: MonadThrow]:
  protected val NotAuthorized: F[Unit] =
    MonadThrow[F].raiseError(new RuntimeException("Not authorized"))

  def setImageQuality(iq:    ImageQuality): F[Unit]    = NotAuthorized
  def setCloudExtinction(ce: CloudExtinction): F[Unit] = NotAuthorized
  def setWaterVapor(wv:      WaterVapor): F[Unit]      = NotAuthorized
  def setSkyBackground(sb:   SkyBackground): F[Unit]   = NotAuthorized
  def refresh(clientId:      ClientId): F[Unit]        = NotAuthorized

object ConfigApi:
  // Default value is NotAuthorized implementations
  val ctx: Context[ConfigApi[IO]] = React.createContext(new ConfigApi[IO] {})
