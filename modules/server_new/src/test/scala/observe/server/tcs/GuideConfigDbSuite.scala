// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.effect.IO
import coulomb.Quantity
import coulomb.syntax.*
import coulomb.units.accepted.Millimeter
import io.circe.parser.*
import lucuma.core.enums.*
import lucuma.core.model.AltairConfig.*
import lucuma.core.model.GemsConfig.*
import lucuma.core.model.GuideConfig
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.model.arb.ArbGuideConfig

class GuideConfigDbSuite extends munit.CatsEffectSuite with ArbGuideConfig with TcsArbitraries {

  val rawJson1: String = """
  {
    "tcsGuide": {
      "mountGuideOn": true,
      "m1Guide": {
        "on": true,
        "source": "PWFS1"
      },
      "m2Guide": {
        "on": true,
        "sources": ["PWFS1"],
        "comaOn": false
      },
      "dayTimeMode": false
    },
    "gaosGuide": null
  }
  """

  val guideConfig1: GuideConfigState = GuideConfigState(
    GuideConfig(
      TelescopeGuideConfig(
        MountGuideOption.MountGuideOn,
        M1GuideConfig.M1GuideOn(M1Source.PWFS1),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOff, Set(TipTiltSource.PWFS1)),
        Some(false),
        None
      ),
      None
    ),
    gemsSkyPaused = false
  )

  val rawJson2: String = """
  {
    "tcsGuide": {
      "m1Guide": {
        "on": true,
        "source": "PWFS1"
      },
      "m2Guide": {
        "on": true,
        "sources": ["PWFS1"],
        "comaOn": true
      },
      "dayTimeMode": false,
      "mountGuideOn": false
    },
    "gaosGuide": {
      "altair": {
        "mode": "LGS",
        "aoOn": true,
        "strapOn": true,
        "sfoOn": true,
        "useOI": false,
        "useP1": false,
        "oiBlend": false,
        "aogsx": -5.0,
        "aogsy": 3.0
      }
    }
  }
  """

  val guideConfig2: GuideConfigState = GuideConfigState(
    GuideConfig(
      TelescopeGuideConfig(
        MountGuideOption.MountGuideOff,
        M1GuideConfig.M1GuideOn(M1Source.PWFS1),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.PWFS1)),
        Some(false),
        None
      ),
      Some(
        Left(
          Lgs(strap = true,
              sfo = true,
              starPos =
                (BigDecimal(-5.0).withUnit[Millimeter], BigDecimal(3.0).withUnit[Millimeter])
          )
        )
      )
    ),
    gemsSkyPaused = false
  )

  val rawJson3: String = """
  {
    "tcsGuide": {
      "m1Guide": {
        "on": true,
        "source": "GAOS"
      },
      "m2Guide": {
        "on": true,
        "sources": ["GAOS"],
        "comaOn": true
      },
      "mountGuideOn": true,
      "dayTimeMode": false
    },
    "gaosGuide": {
      "gems": {
        "aoOn": true,
        "ttgs1On": true,
        "ttgs2On": false,
        "ttgs3On": false,
        "odgw1On": true,
        "odgw2On": false,
        "odgw3On": true,
        "odgw4On": true
      }
    }
 }
  """

  val guideConfig3: GuideConfigState = GuideConfigState(
    GuideConfig(
      TelescopeGuideConfig(
        MountGuideOption.MountGuideOn,
        M1GuideConfig.M1GuideOn(M1Source.GAOS),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.GAOS)),
        Some(false),
        None
      ),
      Some(
        Right(
          GemsOn(
            Cwfs1Usage.Use,
            Cwfs2Usage.DontUse,
            Cwfs3Usage.DontUse,
            Odgw1Usage.Use,
            Odgw2Usage.DontUse,
            Odgw3Usage.Use,
            Odgw4Usage.Use,
            P1Usage.DontUse,
            OIUsage.DontUse
          )
        )
      )
    ),
    gemsSkyPaused = false
  )

  val legacyTccJson: String = """
  {
    "tcsGuide": {
      "m1Guide": {
        "on": true,
        "source": "GAOS"
      },
      "m2Guide": {
        "on": true,
        "sources": ["GAOS"],
        "comaOn": true
      },
      "mountGuideOn": true
    },
    "gaosGuide": {
      "gems": {
        "aoOn": true,
        "ttgs1On": true,
        "ttgs2On": false,
        "ttgs3On": false,
        "odgw1On": true,
        "odgw2On": false,
        "odgw3On": true,
        "odgw4On": true
      }
    }
 }
  """
  val tccConfig             = GuideConfigState(
    GuideConfig(
      TelescopeGuideConfig(
        MountGuideOption.MountGuideOn,
        M1GuideConfig.M1GuideOn(M1Source.GAOS),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.GAOS)),
        None,
        None
      ),
      Some(
        Right(
          GemsOn(
            Cwfs1Usage.Use,
            Cwfs2Usage.DontUse,
            Cwfs3Usage.DontUse,
            Odgw1Usage.Use,
            Odgw2Usage.DontUse,
            Odgw3Usage.Use,
            Odgw4Usage.Use,
            P1Usage.DontUse,
            OIUsage.DontUse
          )
        )
      )
    ),
    gemsSkyPaused = false
  )
  test("GuideConfigDb provide decoders") {
    assertEquals(decode[GuideConfig](rawJson1), Right(guideConfig1.config))
    assertEquals(decode[GuideConfig](rawJson2), Right(guideConfig2.config))
    assertEquals(decode[GuideConfig](rawJson3), Right(guideConfig3.config))
    assertEquals(decode[GuideConfig](legacyTccJson), Right(tccConfig.config))
  }

  test("retrieve the same configuration that was set") {
    val db = GuideConfigDb.newDb[IO]

    db.flatMap(x => x.set(guideConfig1) *> x.value).map(assertEquals(_, guideConfig1))
  }

}
