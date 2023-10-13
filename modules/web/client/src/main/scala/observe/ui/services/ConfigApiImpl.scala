// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import cats.effect.Resource
import crystal.react.View
import crystal.react.*
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Encoder
import io.circe.*
import io.circe.syntax.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import observe.model.ClientId
import observe.ui.model.enums.ApiStatus
import org.http4s.Method
import org.http4s.Uri
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.client.*
import org.http4s.headers.Authorization
import org.typelevel.log4cats.Logger

case class ConfigApiImpl(
  client:    Client[IO],
  baseUri:   Uri,
  token:     NonEmptyString,
  apiStatus: View[ApiStatus],
  latch:     Resource[IO, Unit],
  onError:   Throwable => IO[Unit]
)(using Logger[IO])
    extends ConfigApi[IO]:
  private def request[T: Encoder](path: String, data: T): IO[Unit] =
    latch.use(_ =>
      apiStatus.async.set(ApiStatus.Busy) >>
        client
          .expect[Unit](
            Request(Method.POST, baseUri.addPath(path))
              .withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token.value)))
              .withEntity(data.asJson)
          )
          .onError(onError)
          .onCancel(onError(new Exception("There was an error modifying configuration."))) >>
        apiStatus.async.set(ApiStatus.Idle)
    )

  override def setImageQuality(clientId: ClientId, iq: ImageQuality): IO[Unit]       =
    request(s"${clientId.value}/iq", iq)
  override def setCloudExtinction(clientId: ClientId, ce: CloudExtinction): IO[Unit] =
    request(s"${clientId.value}/ce", ce)
  override def setWaterVapor(clientId: ClientId, wv: WaterVapor): IO[Unit]           =
    request(s"${clientId.value}/wv", wv)
  override def setSkyBackground(clientId: ClientId, sb: SkyBackground): IO[Unit]     =
    request(s"${clientId.value}/sb", sb)
  override def refresh(clientId: ClientId): IO[Unit] = request(s"${clientId.value}/refresh", ())

  override def isBlocked: Boolean = apiStatus.get == ApiStatus.Busy
