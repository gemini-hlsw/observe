// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.{Eq, Show}
import observe.model.enums.MountGuideOption

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
}
