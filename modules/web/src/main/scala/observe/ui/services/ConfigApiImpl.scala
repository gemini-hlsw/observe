// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import io.circe.Encoder
import io.circe.*
import io.circe.syntax.*
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import observe.model.ClientId
import org.http4s.Method
import org.http4s.Uri
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.client.*

case class ConfigApiImpl(client: Client[IO], baseUri: Uri) extends ConfigApi[IO]:
  private def request[T: Encoder](path: String, data: T): IO[Unit] =
    client.expect[Unit](
      // Method.POST(data.asJson, baseUri.addPath(path))
      Request(Method.POST, baseUri.addPath(path)).withEntity(data.asJson)
    )

  def setImageQuality(iq:  ImageQuality): IO[Unit]    = request("/commands/iq", iq)
  def setCloudCover(ce:    CloudExtinction): IO[Unit] = request("/commands/ce", ce)
  def setWaterVapor(wv:    WaterVapor): IO[Unit]      = request("/commands/wv", wv)
  def setSkyBackground(sb: SkyBackground): IO[Unit]   = request("/commands/sb", sb)
  def refresh(clientId:    ClientId): IO[Unit]        = request("/commands/refresh", clientId)
