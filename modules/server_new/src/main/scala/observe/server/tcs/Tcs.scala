// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.syntax.all.*
import lucuma.core.enums.GuideState
import mouse.all.*
import observe.model.enums.{M1Source, NodAndShuffleStage, TipTiltSource}
import observe.model.{M1GuideConfig, M2GuideConfig, TelescopeGuideConfig}
import observe.server.tcs.TcsController.*
import observe.server.{ConfigResult, System}

trait Tcs[F[_]] extends System[F] {
  def nod(stage: NodAndShuffleStage, offset: InstrumentOffset, guided: Boolean): F[ConfigResult[F]]
}

object Tcs {

  val defaultGuiderConf = GuiderConfig(ProbeTrackingConfig.Parked, GuiderSensorOff)
  def calcGuiderConfig(
    inUse:     Boolean,
    guideWith: Option[GuideState]
  ): GuiderConfig =
    guideWith
      .flatMap(v => inUse.option(GuiderConfig(v.toProbeTracking, v.toGuideSensorOption)))
      .getOrElse(defaultGuiderConf)

  // Conversions from ODB model values to TCS configuration values
  extension (guideWith: GuideState) {
    def toProbeTracking: ProbeTrackingConfig = guideWith match {
      case GuideState.Disabled => ProbeTrackingConfig.Frozen
      case GuideState.Enabled  => ProbeTrackingConfig.On(NodChopTrackingConfig.Normal)
    }

    def toGuideSensorOption: GuiderSensorOption = guideWith match {
      case GuideState.Disabled => GuiderSensorOff
      case GuideState.Enabled  => GuiderSensorOff
    }
  }

  def calcGuiderInUse(
    telGuide:      TelescopeGuideConfig,
    tipTiltSource: TipTiltSource,
    m1Source:      M1Source
  ): Boolean = {
    val usedByM1: Boolean = telGuide.m1Guide match {
      case M1GuideConfig.M1GuideOn(src) => src === m1Source
      case _                            => false
    }
    val usedByM2          = telGuide.m2Guide match {
      case M2GuideConfig.M2GuideOn(_, srcs) => srcs.contains(tipTiltSource)
      case _                                => false
    }

    usedByM1 | usedByM2
  }

}
