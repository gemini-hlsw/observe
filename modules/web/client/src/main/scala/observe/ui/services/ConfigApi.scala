// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.MonadThrow
import cats.effect.IO
import japgolly.scalajs.react.React
import japgolly.scalajs.react.feature.Context
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.CloudExtinction
import lucuma.core.model.ImageQuality
import lucuma.core.model.Observation
import observe.model.Observer
import observe.model.Operator
import observe.model.SubsystemEnabled

import scala.annotation.unused

trait ConfigApi[F[_]: MonadThrow]:
  def setImageQuality(@unused iq:    ImageQuality): F[Unit]    = NotAuthorized
  def setCloudExtinction(@unused ce: CloudExtinction): F[Unit] = NotAuthorized
  def setWaterVapor(@unused wv:      WaterVapor): F[Unit]      = NotAuthorized
  def setSkyBackground(@unused b:    SkyBackground): F[Unit]   = NotAuthorized

  def setOperator(@unused operator: Option[Operator]): F[Unit] = NotAuthorized
  def setObserver(@unused obsId: Observation.Id, @unused observer: Option[Observer]): F[Unit] =
    NotAuthorized

  def setTcsEnabled(@unused obsId: Observation.Id, @unused enabled: SubsystemEnabled): F[Unit]  =
    NotAuthorized
  def setGcalEnabled(@unused obsId: Observation.Id, @unused enabled: SubsystemEnabled): F[Unit] =
    NotAuthorized
  def setInstrumentEnabled(
    @unused obsId:   Observation.Id,
    @unused enabled: SubsystemEnabled
  ): F[Unit] =
    NotAuthorized
  def setDhsEnabled(@unused obsId: Observation.Id, @unused enabled: SubsystemEnabled): F[Unit]  =
    NotAuthorized

  def isBusy: Boolean = false

object ConfigApi:
  // Default value is NotAuthorized implementations
  val ctx: Context[ConfigApi[IO]] = React.createContext(new ConfigApi[IO] {})
