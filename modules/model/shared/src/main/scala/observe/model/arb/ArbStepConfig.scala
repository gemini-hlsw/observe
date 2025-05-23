// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.arb.*
import lucuma.core.math.Angle
import lucuma.core.math.Offset
import lucuma.core.math.arb.ArbOffset.given
import observe.model.*
import org.scalacheck.Arbitrary.*
import org.scalacheck.Gen

trait ArbStepConfig {
  val asciiStr: Gen[String] =
    Gen.listOf(Gen.alphaChar).map(_.mkString)

  val stepItemG: Gen[(String, String)] =
    for {
      a <- asciiStr
      b <- asciiStr
    } yield (a, b)

  private val perturbations: List[String => Gen[String]] =
    List(s => if (s.startsWith("-")) Gen.const(s) else Gen.const(s"00%s") // insert leading 0s
    )

  // Strings that are often parsable as Offsets
  val stringsOffsets: Gen[String] =
    arbitrary[Offset]
      .map(x => Angle.arcseconds.get(x.p.toAngle).toString)
      .flatMapOneOf(Gen.const, perturbations*)
}

object ArbStepConfig extends ArbStepConfig
