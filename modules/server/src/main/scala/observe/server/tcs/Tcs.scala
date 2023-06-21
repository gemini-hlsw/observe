// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.syntax.all.*
import edu.gemini.spModel.guide.StandardGuideOptions
import mouse.all.*
import observe.model.M1GuideConfig
import observe.model.M2GuideConfig
import observe.model.TelescopeGuideConfig
import observe.model.enums.M1Source
import observe.model.enums.NodAndShuffleStage
import observe.model.enums.TipTiltSource
import observe.server.ConfigResult
import observe.server.System
import observe.server.tcs.TcsController.*

trait Tcs[F[_]] extends System[F] {
  def nod(stage: NodAndShuffleStage, offset: InstrumentOffset, guided: Boolean): F[ConfigResult]
}

object Tcs {

  val defaultGuiderConf = GuiderConfig(ProbeTrackingConfig.Parked, GuiderSensorOff)
  def calcGuiderConfig(
    inUse:     Boolean,
    guideWith: Option[StandardGuideOptions.Value]
  ): GuiderConfig =
    guideWith
      .flatMap(v => inUse.option(GuiderConfig(v.toProbeTracking, v.toGuideSensorOption)))
      .getOrElse(defaultGuiderConf)

  // Conversions from ODB model values to TCS configuration values
  implicit class GuideWithOps(guideWith: StandardGuideOptions.Value) {
    val toProbeTracking: ProbeTrackingConfig = guideWith match {
      case StandardGuideOptions.Value.park   => ProbeTrackingConfig.Parked
      case StandardGuideOptions.Value.freeze => ProbeTrackingConfig.Frozen
      case StandardGuideOptions.Value.guide  => ProbeTrackingConfig.On(NodChopTrackingConfig.Normal)
    }

    val toGuideSensorOption: GuiderSensorOption =
      if (guideWith.isActive) GuiderSensorOn
      else GuiderSensorOff
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
