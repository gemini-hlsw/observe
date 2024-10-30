// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import lucuma.core.enums.{Site, SkyBackground, WaterVapor}
import lucuma.core.model.ConstraintSet
import observe.model.Conditions

object ConditionOps {
  
  extension (wv: WaterVapor) {
    def toMillimeters(site: Site): Double = site match
      case Site.GN => wv match
        case WaterVapor.VeryDry => 1.0
        case WaterVapor.Dry => 1.6
        case WaterVapor.Median => 3.0
        case WaterVapor.Wet => 5.0
      case Site.GS => wv match
        case WaterVapor.VeryDry => 2.3
        case WaterVapor.Dry => 4.3
        case WaterVapor.Median => 7.6
        case WaterVapor.Wet => 10.0
  }

  extension (sb: SkyBackground) {
    def toMicroVolts: Double = sb match
      case SkyBackground.Darkest => 21.3
      case SkyBackground.Dark => 20.7
      case SkyBackground.Gray => 19.5
      case SkyBackground.Bright => 18.0
  }
  
  extension (conditions: Conditions) {
    def imageQualityStr: String = conditions.iq.map(_.label).getOrElse("Any")
    def cloudExtinctionStr: String = conditions.ce.map(_.label).getOrElse("Any")
    def waterVaporStr: String = conditions.wv.map(_.label).getOrElse("Any")
    def backgroundLightStr: String = conditions.sb.map(_.label).getOrElse("Any")
    def imageQualityDbl: Double = conditions.iq.map(_.toArcSeconds.value.toDouble).getOrElse(Double.NaN)
    def cloudExtinctionDbl: Double = conditions.ce.map(_.toBrightness).getOrElse(Double.NaN)
    def waterVaporDbl(site: Site): Double = conditions.wv.map(_.toMillimeters(site)).getOrElse(Double.NaN)
    def backgroundLightDbl: Double = conditions.sb.map(_.toMicroVolts).getOrElse(Double.NaN)
  }
  
  extension (cnd: ConstraintSet) {
    def imageQualityStr: String = cnd.imageQuality.label
    def cloudExtinctionStr: String = cnd.cloudExtinction.label
    def waterVaporStr: String = cnd.waterVapor.label
    def backgroundLightStr: String = cnd.skyBackground.label
    def imageQualityDbl: Double = cnd.imageQuality.toArcSeconds.value.toDouble
    def cloudExtinctionDbl: Double = cnd.cloudExtinction.toBrightness
    def waterVaporDbl(site: Site): Double = cnd.waterVapor.toMillimeters(site)
    def backgroundLightDbl: Double = cnd.skyBackground.toMicroVolts
  }

}
