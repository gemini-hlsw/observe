// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import algebra.instances.all.given
import coulomb.*
import coulomb.policy.spire.standard.given
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import coulomb.units.accepted.Degree
import edu.gemini.observe.server.tcs.BinaryOnOff
import edu.gemini.observe.server.tcs.BinaryYesNo
import observe.model.TelescopeGuideConfig
import observe.model.arb.ArbTelescopeGuideConfig.given
import observe.server.altair.AltairController.AltairConfig
import observe.server.altair.ArbAltairConfig.given
import observe.server.gems.ArbGemsConfig.given
import observe.server.gems.GemsController.GemsConfig
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait TcsArbitraries {
  given Arbitrary[TcsController.Beam]    = Arbitrary(
    Gen.oneOf(TcsController.Beam.A, TcsController.Beam.B, TcsController.Beam.C)
  )
  given Cogen[TcsController.Beam]        =
    Cogen[String].contramap(_.productPrefix)
  given Arbitrary[TcsController.NodChop] = Arbitrary {
    for {
      n <- arbitrary[TcsController.Beam]
      c <- arbitrary[TcsController.Beam]
    } yield TcsController.NodChop(n, c)
  }
  given Cogen[TcsController.NodChop]     =
    Cogen[(TcsController.Beam, TcsController.Beam)].contramap(x => (x.nod, x.chop))

  private def rangedAngleGen(
    minVal: Quantity[Double, ArcSecond],
    maxVal: Quantity[Double, ArcSecond]
  ) =
    Gen.choose(minVal.value, maxVal.value)

  private val offsetLimit: Quantity[Double, ArcSecond] = 120.0.withUnit[ArcSecond]

  given Arbitrary[TcsController.OffsetP]          = Arbitrary(
    rangedAngleGen(-offsetLimit, offsetLimit).map(u =>
      TcsController.OffsetP.apply(u.withUnit[Degree])
    )
  )
  given Cogen[TcsController.OffsetP]              =
    Cogen[Double].contramap(_.value.value)
  given Arbitrary[TcsController.OffsetQ]          = Arbitrary(
    rangedAngleGen(-offsetLimit, offsetLimit).map(u =>
      TcsController.OffsetQ.apply(u.withUnit[Degree])
    )
  )
  given Cogen[TcsController.OffsetQ]              =
    Cogen[Double].contramap(_.value.value)
  given Arbitrary[TcsController.InstrumentOffset] = Arbitrary {
    for {
      p <- arbitrary[TcsController.OffsetP]
      q <- arbitrary[TcsController.OffsetQ]
    } yield TcsController.InstrumentOffset(p, q)
  }
  given Cogen[TcsController.InstrumentOffset]     =
    Cogen[(TcsController.OffsetP, TcsController.OffsetQ)].contramap(x => (x.p, x.q))

  given Arbitrary[Quantity[Double, ArcSecond]] = Arbitrary(
    rangedAngleGen(-90.withUnit[Degree], 270.withUnit[Degree]).map(_.withUnit[ArcSecond])
  )
  given Cogen[Quantity[Double, ArcSecond]]     = Cogen[Double].contramap(_.value)

  given Arbitrary[CRFollow] = Arbitrary {
    Gen.oneOf(CRFollow.On, CRFollow.Off)
  }
  given Cogen[CRFollow]     =
    Cogen[String].contramap(_.productPrefix)

  given Arbitrary[BinaryYesNo] = Arbitrary(
    Gen.oneOf(BinaryYesNo.Yes, BinaryYesNo.No)
  )
  given Cogen[BinaryYesNo]     =
    Cogen[String].contramap(_.name)
  given Arbitrary[BinaryOnOff] = Arbitrary(
    Gen.oneOf(BinaryOnOff.Off, BinaryOnOff.On)
  )
  given Cogen[BinaryOnOff]     =
    Cogen[String].contramap(_.name)

  given Arbitrary[GuideConfig] = Arbitrary {
    for {
      tg <- arbitrary[TelescopeGuideConfig]
      gc <- arbitrary[Option[Either[AltairConfig, GemsConfig]]]
      p  <- arbitrary[Boolean]
    } yield GuideConfig(tg, gc, p)
  }
}
