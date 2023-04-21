// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import edu.gemini.observe.server.tcs.{BinaryOnOff, BinaryYesNo}
import org.scalacheck.Arbitrary.*
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen
import shapeless.tag
import shapeless.tag.@@
import squants.Angle
import squants.space.AngleConversions.*
import squants.space.Degrees

trait TcsArbitraries {
  given Arbitrary[TcsController.Beam]       = Arbitrary(
    Gen.oneOf(TcsController.Beam.A, TcsController.Beam.B, TcsController.Beam.C)
  )
  given Cogen[TcsController.Beam]         =
    Cogen[String].contramap(_.productPrefix)
  given Arbitrary[TcsController.NodChop] = Arbitrary {
    for {
      n <- arbitrary[TcsController.Beam]
      c <- arbitrary[TcsController.Beam]
    } yield TcsController.NodChop(n, c)
  }
  given Cogen[TcsController.NodChop]   =
    Cogen[(TcsController.Beam, TcsController.Beam)].contramap(x => (x.nod, x.chop))

  private def rangedAngleGen(minVal: Angle, maxVal: Angle) =
    Gen.choose(minVal.toDegrees, maxVal.toDegrees).map(Degrees(_))

  private val offsetLimit: Angle = 120.arcseconds

  given Arbitrary[Angle @@ TcsController.OffsetP] = Arbitrary(
    rangedAngleGen(-offsetLimit, offsetLimit).map(tag[TcsController.OffsetP].apply)
  )
  given Cogen[Angle @@ TcsController.OffsetP]   =
    Cogen[Double].contramap(_.value)
  given Arbitrary[Angle @@ TcsController.OffsetQ] = Arbitrary(
    rangedAngleGen(-offsetLimit, offsetLimit).map(tag[TcsController.OffsetQ].apply)
  )
  given Cogen[Angle @@ TcsController.OffsetQ]   =
    Cogen[Double].contramap(_.value)
  given Arbitrary[TcsController.InstrumentOffset]     = Arbitrary {
    for {
      p <- arbitrary[Angle @@ TcsController.OffsetP]
      q <- arbitrary[Angle @@ TcsController.OffsetQ]
    } yield TcsController.InstrumentOffset(p, q)
  }
  given Cogen[TcsController.InstrumentOffset]       =
    Cogen[(Angle @@ TcsController.OffsetP, Angle @@ TcsController.OffsetQ)].contramap(x =>
      (x.p, x.q)
    )

  given Arbitrary[Angle] = Arbitrary(rangedAngleGen(-90.degrees, 270.degrees))
  given Cogen[Angle]   = Cogen[Double].contramap(_.toDegrees)

  given Arbitrary[CRFollow] = Arbitrary {
    Gen.oneOf(CRFollow.On, CRFollow.Off)
  }
  given Cogen[CRFollow]   =
    Cogen[String].contramap(_.productPrefix)

  given Arbitrary[BinaryYesNo]      = Arbitrary(
    Gen.oneOf(BinaryYesNo.Yes, BinaryYesNo.No)
  )
  given Cogen[BinaryYesNo] =
    Cogen[String].contramap(_.name)
  given Arbitrary[BinaryOnOff]      = Arbitrary(
    Gen.oneOf(BinaryOnOff.Off, BinaryOnOff.On)
  )
  given Cogen[BinaryOnOff] =
    Cogen[String].contramap(_.name)
}
