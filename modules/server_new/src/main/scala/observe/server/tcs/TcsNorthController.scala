// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.Show
import cats.data.NonEmptySet
import cats.implicits.*
import lucuma.core.enums.Site
import lucuma.core.enums.Site.GN
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import monocle.Focus
import monocle.Lens
import observe.model.enums.NodAndShuffleStage
import observe.server.altair.Altair
import observe.server.tcs.TcsController.GuiderConfig.given
import observe.server.tcs.TcsController.*

trait TcsNorthController[F[_]] {
  import TcsNorthController.*

  def applyConfig(
    subsystems: NonEmptySet[TcsController.Subsystem],
    gaos:       Option[Altair[F]],
    tc:         TcsNorthConfig
  ): F[Unit]

  def notifyObserveStart: F[Unit]

  def notifyObserveEnd: F[Unit]

  def nod(
    subsystems: NonEmptySet[Subsystem],
    tcsConfig:  TcsNorthConfig
  )(stage: NodAndShuffleStage, offset: InstrumentOffset, guided: Boolean): F[Unit]

}

object TcsNorthController {

  type TcsNorthConfig   = TcsConfig[Site.GN.type]
  type TcsNorthAoConfig = AoTcsConfig[Site.GN.type]

  object TcsNorthAoConfig {
    val m1Guide: Lens[AoTcsConfig[GN.type], M1GuideConfig] =
      Focus[TcsNorthAoConfig](_.gc.m1Guide)
    val m2Guide: Lens[AoTcsConfig[GN.type], M2GuideConfig] =
      Focus[TcsNorthAoConfig](_.gc.m2Guide)
  }

  given Show[AoGuide] =
    Show.show(_.value.show)

  given Show[TcsNorthConfig] = Show.show {
    case x: BasicTcsConfig[Site.GN.type] => x.show
    case x: TcsNorthAoConfig             => x.toString
  }

}
