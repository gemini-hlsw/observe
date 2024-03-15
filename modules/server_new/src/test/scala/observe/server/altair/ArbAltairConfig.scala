// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.altair

import coulomb.Quantity
import coulomb.testkit.given
import coulomb.units.accepted.Millimeter
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

import AltairController.{AltairConfig, AltairOff, Lgs, LgsWithOi, LgsWithP1, Ngs}

trait ArbAltairConfig {

  given Arbitrary[Lgs] = Arbitrary {
    for {
      st <- arbitrary[Boolean]
      sf <- arbitrary[Boolean]
      l1 <- arbitrary[Quantity[Double, Millimeter]]
      l2 <- arbitrary[Quantity[Double, Millimeter]]
    } yield Lgs(st, sf, (l1, l2))
  }

  given Cogen[Lgs] =
    Cogen[(Boolean, Boolean, Quantity[Double, Millimeter], Quantity[Double, Millimeter])]
      .contramap { x =>
        (x.strap, x.sfo, x.starPos._1, x.starPos._2)
      }

  given Arbitrary[Ngs] = Arbitrary {
    for {
      b  <- arbitrary[Boolean]
      l1 <- arbitrary[Quantity[Double, Millimeter]]
      l2 <- arbitrary[Quantity[Double, Millimeter]]
    } yield Ngs(b, (l1, l2))
  }

  given Cogen[Ngs] =
    Cogen[(Boolean, Quantity[Double, Millimeter], Quantity[Double, Millimeter])].contramap { x =>
      (x.blend, x.starPos._1, x.starPos._2)
    }

  given Arbitrary[AltairConfig] = Arbitrary {
    Gen.oneOf(arbitrary[Lgs],
              arbitrary[Ngs],
              Gen.const(AltairOff),
              Gen.const(LgsWithP1),
              Gen.const(LgsWithOi)
    )
  }

  given Cogen[AltairConfig] = Cogen[Option[
    Option[Option[Either[(Boolean,
                          Boolean,
                          Quantity[Double, Millimeter],
                          Quantity[Double, Millimeter]
                         ),
                         (Boolean, Quantity[Double, Millimeter], Quantity[Double, Millimeter])
    ]]]
  ]].contramap {
    case AltairOff    => None
    case LgsWithP1    => Some(None)
    case LgsWithOi    => Some(Some(None))
    case Lgs(a, b, c) => Some(Some(Some(Left(a, b, c._1, c._2))))
    case Ngs(a, c)    => Some(Some(Some(Right(a, c._1, c._2))))
  }

}

object ArbAltairConfig extends ArbAltairConfig
