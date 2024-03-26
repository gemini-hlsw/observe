// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all.*
import crystal.react.*
import crystal.react.View
import io.circe.*
import io.circe.Encoder
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.Observation
import observe.model.ClientId
import observe.model.Observer
import observe.model.Operator
import observe.model.SubsystemEnabled
import observe.ui.model.enums.ApiStatus
import org.http4s.*
import org.http4s.Uri
import org.http4s.Uri.Path
import org.typelevel.log4cats.Logger

case class ConfigApiImpl(
  client:    ApiClient,
  apiStatus: View[ApiStatus],
  latch:     Resource[IO, Unit]
)(using Logger[IO])
    extends ConfigApi[IO]:
  private def request[T: Encoder](path: Path, data: T): IO[Unit] =
    latch.use: _ =>
      apiStatus.async.set(ApiStatus.Busy) >>
        client
          .post(path, data)
          .flatTap(_ => apiStatus.async.set(ApiStatus.Idle))

  override def setImageQuality(iq: ImageQuality): IO[Unit]       =
    request(Uri.Path.empty / client.clientId.value / "iq", iq)
  override def setCloudExtinction(ce: CloudExtinction): IO[Unit] =
    request(Uri.Path.empty / client.clientId.value / "ce", ce)
  override def setWaterVapor(wv: WaterVapor): IO[Unit]           =
    request(Uri.Path.empty / client.clientId.value / "wv", wv)
  override def setSkyBackground(sb: SkyBackground): IO[Unit]     =
    request(Uri.Path.empty / client.clientId.value / "sb", sb)

  override def setOperator(operator: Option[Operator]): IO[Unit]                        =
    request(
      Uri.Path.empty / client.clientId.value / "operator" / operator.map(_.toString).orEmpty,
      ""
    )
  override def setObserver(obsId: Observation.Id, observer: Option[Observer]): IO[Unit] =
    request(
      Uri.Path.empty / obsId.toString / client.clientId.value / "observer" /
        observer.map(_.toString).orEmpty,
      ""
    )

  override def setTcsEnabled(obsId: Observation.Id, enabled: SubsystemEnabled): IO[Unit] =
    request(
      Uri.Path.empty / obsId.toString / client.clientId.value / "tcsEnabled" / enabled.value,
      ""
    )

  override def setGcalEnabled(obsId: Observation.Id, enabled: SubsystemEnabled): IO[Unit] =
    request(
      Uri.Path.empty / obsId.toString / client.clientId.value / "gcalEnabled" / enabled.value,
      ""
    )

  override def setInstrumentEnabled(obsId: Observation.Id, enabled: SubsystemEnabled): IO[Unit] =
    request(
      Uri.Path.empty / obsId.toString / client.clientId.value / "instrumentEnabled" / enabled.value,
      ""
    )

  override def setDhsEnabled(obsId: Observation.Id, enabled: SubsystemEnabled): IO[Unit] =
    request(
      Uri.Path.empty / obsId.toString / client.clientId.value / "dhsEnabled" / enabled.value,
      ""
    )

  override def isBlocked: Boolean = apiStatus.get == ApiStatus.Busy
