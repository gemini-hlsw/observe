// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import lucuma.core.enums.ImageQuality
import lucuma.core.enums.WaterVapor
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.CloudExtinction
import observe.model.ClientId
import japgolly.scalajs.react.feature.Context
import cats.effect.IO
import japgolly.scalajs.react.React

trait ConfigApi[F[_]]:
  def setImageQuality(iq:  ImageQuality): F[Unit]
  def setCloudCover(ce:    CloudExtinction): F[Unit]
  def setWaterVapor(wv:    WaterVapor): F[Unit]
  def setSkyBackground(sb: SkyBackground): F[Unit]
  def refresh(clientId:    ClientId): F[Unit]

object ConfigApi:
  val ctx: Context[ConfigApi[IO]] = React.createContext(null) // No default value
