// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import lucuma.core.math.Angle
import lucuma.core.math.Wavelength
import lucuma.core.model.ImageQuality
import munit.FunSuite

import ConditionOps.*

class ConditionOpsSuite extends FunSuite {

  test("ConditionOps calculate image quality percentile from double value") {
    assertEquals(
      ImageQuality.Preset.PointSix.toImageQuality.toPercentile(
        Wavelength.unsafeFromIntPicometers(475000),
        Angle.Angle90
      ),
      20
    )
    assertEquals(
      ImageQuality.Preset.PointSix.toImageQuality.toPercentile(
        Wavelength.unsafeFromIntPicometers(630000),
        Angle.Angle90
      ),
      70
    )
    assertEquals(
      ImageQuality.Preset.PointEight.toImageQuality.toPercentile(
        Wavelength.unsafeFromIntPicometers(1200000),
        Angle.Angle90
      ),
      85
    )
    assertEquals(
      ImageQuality.Preset.OnePointZero.toImageQuality.toPercentile(
        Wavelength.unsafeFromIntPicometers(1200000),
        Angle.Angle90
      ),
      100
    )
    assertEquals(
      ImageQuality.Preset.OnePointZero.toImageQuality.toPercentile(
        Wavelength.unsafeFromIntPicometers(1650000),
        Angle.Angle90
      ),
      100
    )
    assertEquals(
      ImageQuality.Preset.PointSix.toImageQuality.toPercentile(
        Wavelength.unsafeFromIntPicometers(11700000),
        Angle.Angle90
      ),
      100
    )
  }

}
