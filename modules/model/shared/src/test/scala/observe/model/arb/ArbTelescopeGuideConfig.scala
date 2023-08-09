// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import lucuma.core.util.arb.ArbEnumerated.*
import observe.model.TelescopeGuideConfig
import observe.model.enums.*
import observe.model.M1GuideConfig
import observe.model.M2GuideConfig
import observe.model.arb.ArbM1GuideConfig.given
import observe.model.arb.ArbM2GuideConfig.given

trait ArbTelescopeGuideConfig {

  given arbTelescopeGuideOn: Arbitrary[TelescopeGuideConfig] =
    Arbitrary {
      for {
        mo <- arbitrary[MountGuideOption]
        m1 <- arbitrary[M1GuideConfig]
        m2 <- arbitrary[M2GuideConfig]
      } yield TelescopeGuideConfig(mo, m1, m2)
    }

  given telescopeConfigCogen: Cogen[TelescopeGuideConfig] =
    Cogen[(MountGuideOption, M1GuideConfig, M2GuideConfig)]
      .contramap(x => (x.mountGuide, x.m1Guide, x.m2Guide))
}

object ArbTelescopeGuideConfig extends ArbTelescopeGuideConfig
