// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gems

import cats.syntax.all.*
import org.scalacheck.{Arbitrary, Cogen}
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen.*
import org.scalacheck.Gen
import GemsController.{
  Cwfs1Usage,
  Cwfs2Usage,
  Cwfs3Usage,
  GemsConfig,
  GemsOff,
  GemsOn,
  OIUsage,
  Odgw1Usage,
  Odgw2Usage,
  Odgw3Usage,
  Odgw4Usage,
  P1Usage
}
import observe.server.gems.GemsController.Cwfs1Usage.given
import observe.server.gems.GemsController.Cwfs2Usage.given
import observe.server.gems.GemsController.Cwfs3Usage.given
import observe.server.gems.GemsController.OIUsage.given
import observe.server.gems.GemsController.Odgw1Usage.given
import observe.server.gems.GemsController.Odgw2Usage.given
import observe.server.gems.GemsController.Odgw3Usage.given
import observe.server.gems.GemsController.Odgw4Usage.given
import observe.server.gems.GemsController.P1Usage.given

trait ArbGemsConfig {

  given Arbitrary[Cwfs1Usage] = Arbitrary(
    Gen.oneOf(Cwfs1Usage.Use, Cwfs1Usage.DontUse)
  )

  given Arbitrary[Cwfs2Usage] = Arbitrary(
    Gen.oneOf(Cwfs2Usage.Use, Cwfs2Usage.DontUse)
  )

  given Arbitrary[Cwfs3Usage] = Arbitrary(
    Gen.oneOf(Cwfs3Usage.Use, Cwfs3Usage.DontUse)
  )

  given Arbitrary[Odgw1Usage] = Arbitrary(
    Gen.oneOf(Odgw1Usage.Use, Odgw1Usage.DontUse)
  )

  given Arbitrary[Odgw2Usage] = Arbitrary(
    Gen.oneOf(Odgw2Usage.Use, Odgw2Usage.DontUse)
  )

  given Arbitrary[Odgw3Usage] = Arbitrary(
    Gen.oneOf(Odgw3Usage.Use, Odgw3Usage.DontUse)
  )

  given Arbitrary[Odgw4Usage] = Arbitrary(
    Gen.oneOf(Odgw4Usage.Use, Odgw4Usage.DontUse)
  )

  given Arbitrary[OIUsage] = Arbitrary(
    Gen.oneOf(OIUsage.Use, OIUsage.DontUse)
  )

  given Arbitrary[P1Usage] = Arbitrary(
    Gen.oneOf(P1Usage.Use, P1Usage.DontUse)
  )

  given Arbitrary[GemsOn] = Arbitrary {
    for {
      c1  <- arbitrary[Cwfs1Usage]
      c2  <- arbitrary[Cwfs2Usage]
      c3  <- arbitrary[Cwfs3Usage]
      od1 <- arbitrary[Odgw1Usage]
      od2 <- arbitrary[Odgw2Usage]
      od3 <- arbitrary[Odgw3Usage]
      od4 <- arbitrary[Odgw4Usage]
      oi  <- arbitrary[OIUsage]
      p1  <- arbitrary[P1Usage]
    } yield GemsOn(c1, c2, c3, od1, od2, od3, od4, p1, oi)
  }

  given Cogen[GemsOn] =
    Cogen[(Boolean, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean)]
      .contramap(x =>
        (
          x.cwfs1 === Cwfs1Usage.Use,
          x.cwfs2 === Cwfs2Usage.Use,
          x.cwfs3 === Cwfs3Usage.Use,
          x.odgw1 === Odgw1Usage.Use,
          x.odgw2 === Odgw2Usage.Use,
          x.odgw3 === Odgw3Usage.Use,
          x.odgw4 === Odgw4Usage.Use,
          x.useP1 === P1Usage.Use,
          x.useOI === OIUsage.Use
        )
      )

  given Arbitrary[GemsConfig] = Arbitrary {
    Gen.oneOf(arbitrary[GemsOn], Gen.const(GemsOff))
  }

  given Cogen[GemsConfig] = Cogen[Option[GemsOn]].contramap {
    case GemsOff   => None
    case x: GemsOn => Some(x)
  }

}

object ArbGemsConfig extends ArbGemsConfig
