// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.Show
import observe.model.enums.MountGuideOption
import monocle.{Focus, Lens}

/** Data type for guide config. */
final case class TelescopeGuideConfig(
  mountGuide: MountGuideOption,
  m1Guide:    M1GuideConfig,
  m2Guide:    M2GuideConfig
)

object TelescopeGuideConfig {
  given Eq[TelescopeGuideConfig] =
    Eq.by(x => (x.mountGuide, x.m1Guide, x.m2Guide))

  given Show[TelescopeGuideConfig] = Show.fromToString[TelescopeGuideConfig]

  val mountGuide: Lens[TelescopeGuideConfig, MountGuideOption] =
    Focus[TelescopeGuideConfig](_.mountGuide)
  val m1Guide: Lens[TelescopeGuideConfig, M1GuideConfig]       = Focus[TelescopeGuideConfig](_.m1Guide)
  val m2Guide: Lens[TelescopeGuideConfig, M2GuideConfig]       = Focus[TelescopeGuideConfig](_.m2Guide)

}
