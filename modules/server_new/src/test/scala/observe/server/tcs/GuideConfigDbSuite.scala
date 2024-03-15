// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.effect.IO
import coulomb.Quantity
import coulomb.syntax.*
import coulomb.units.accepted.Millimeter
import io.circe.parser.*
import monocle.law.discipline.LensTests
import monocle.law.discipline.OptionalTests
import observe.model.M1GuideConfig
import observe.model.M2GuideConfig
import observe.model.TelescopeGuideConfig
import observe.model.arb.all.given
import observe.model.enums.*
import observe.server.altair.AltairController
import observe.server.altair.AltairController.Lgs
import observe.server.altair.ArbAltairConfig.given
import observe.server.gems.ArbGemsConfig.given
import observe.server.gems.GemsController.Cwfs1Usage
import observe.server.gems.GemsController.Cwfs2Usage
import observe.server.gems.GemsController.Cwfs3Usage
import observe.server.gems.GemsController.GemsOn
import observe.server.gems.GemsController.OIUsage
import observe.server.gems.GemsController.Odgw1Usage
import observe.server.gems.GemsController.Odgw2Usage
import observe.server.gems.GemsController.Odgw3Usage
import observe.server.gems.GemsController.Odgw4Usage
import observe.server.gems.GemsController.P1Usage
import observe.server.tcs.GuideConfig.given
import observe.server.tcs.GuideConfigDb.given
import org.scalacheck.Arbitrary.*

final class GuideConfigDbSuite
    extends munit.CatsEffectSuite
    with munit.DisciplineSuite
    with TcsArbitraries {

  val rawJson1: String          = """
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
      }
    },
    "gaosGuide": null
  }
  """
  val guideConfig1: GuideConfig = GuideConfig(
    TelescopeGuideConfig(
      MountGuideOption.MountGuideOn,
      M1GuideConfig.M1GuideOn(M1Source.PWFS1),
      M2GuideConfig.M2GuideOn(ComaOption.ComaOff, Set(TipTiltSource.PWFS1))
    ),
    None,
    gemsSkyPaused = false
  )

  val rawJson2: String          = """
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
  val guideConfig2: GuideConfig = GuideConfig(
    TelescopeGuideConfig(
      MountGuideOption.MountGuideOff,
      M1GuideConfig.M1GuideOn(M1Source.PWFS1),
      M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.PWFS1))
    ),
    Some(
      Left(
        Lgs(strap = true,
            sfo = true,
            starPos = (-5.0.withUnit[Millimeter], 3.0.withUnit[Millimeter])
        )
      )
    ),
    gemsSkyPaused = false
  )

  val rawJson3: String          = """
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
  val guideConfig3: GuideConfig = GuideConfig(
    TelescopeGuideConfig(
      MountGuideOption.MountGuideOn,
      M1GuideConfig.M1GuideOn(M1Source.GAOS),
      M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.GAOS))
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
    ),
    gemsSkyPaused = false
  )

  test("GuideConfigDb provide decoders") {
    assertEquals(decode[GuideConfig](rawJson1), Right(guideConfig1))
    assertEquals(decode[GuideConfig](rawJson2), Right(guideConfig2))
    assertEquals(decode[GuideConfig](rawJson3), Right(guideConfig3))
  }

  test("retrieve the same configuration that was set") {
    val db = GuideConfigDb.newDb[IO]

    db.flatMap(x => x.set(guideConfig1) *> x.value).map(assertEquals(_, guideConfig1))
  }

  checkAll("TCS guide lens", LensTests(GuideConfig.tcsGuide))
  checkAll("TCS GAOS configuration lens", LensTests(GuideConfig.gaosGuide))
  checkAll("TCS GeMS guide lens", OptionalTests(GuideConfig.gemsGuide))
  checkAll("TCS Altair guide lens", OptionalTests(GuideConfig.altairGuide))
  checkAll("TCS GeMS sky flag lens", LensTests(GuideConfig.gemsSkyPaused))

}
