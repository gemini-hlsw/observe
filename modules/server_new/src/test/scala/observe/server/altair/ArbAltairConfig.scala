// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.altair

import org.scalacheck.Arbitrary.*
import org.scalacheck.{Arbitrary, Cogen, Gen}
import observe.model.arb.all.given
import squants.Length

import AltairController.{AltairConfig, AltairOff, Lgs, LgsWithOi, LgsWithP1, Ngs}

trait ArbAltairConfig {

  given Arbitrary[Lgs] = Arbitrary {
    for {
      st <- arbitrary[Boolean]
      sf <- arbitrary[Boolean]
      l1 <- arbitrary[Length]
      l2 <- arbitrary[Length]
    } yield Lgs(st, sf, (l1, l2))
  }

  given Cogen[Lgs] = Cogen[(Boolean, Boolean, Length, Length)].contramap { x =>
    (x.strap, x.sfo, x.starPos._1, x.starPos._2)
  }

  given Arbitrary[Ngs] = Arbitrary {
    for {
      b  <- arbitrary[Boolean]
      l1 <- arbitrary[Length]
      l2 <- arbitrary[Length]
    } yield Ngs(b, (l1, l2))
  }

  given Cogen[Ngs] = Cogen[(Boolean, Length, Length)].contramap { x =>
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
    Option[Option[Either[(Boolean, Boolean, Length, Length), (Boolean, Length, Length)]]]
  ]].contramap {
    case AltairOff    => None
    case LgsWithP1    => Some(None)
    case LgsWithOi    => Some(Some(None))
    case Lgs(a, b, c) => Some(Some(Some(Left(a, b, c._1, c._2))))
    case Ngs(a, c)    => Some(Some(Some(Right(a, c._1, c._2))))
  }

}

object ArbAltairConfig extends ArbAltairConfig
