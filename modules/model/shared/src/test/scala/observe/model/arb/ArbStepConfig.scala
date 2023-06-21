// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.*
import org.scalacheck.Cogen.*
import lucuma.core.arb.*
import lucuma.core.util.arb.ArbEnumerated.*
import lucuma.core.math.Angle
import lucuma.core.math.Offset
import lucuma.core.math.arb.ArbOffset.*
import observe.model.*
import observe.model.enums.*

trait ArbStepConfig {
  val asciiStr: Gen[String] =
    Gen.listOf(Gen.alphaChar).map(_.mkString)

  val stepItemG: Gen[(String, String)] =
    for {
      a <- asciiStr
      b <- asciiStr
    } yield (a, b)

  val parametersGen: Gen[Parameters] =
    Gen.chooseNum(0, 10).flatMap(s => Gen.mapOfN[String, String](s, stepItemG))

  val stepConfigG: Gen[(SystemName, Parameters)] =
    for {
      a <- arbitrary[SystemName]
      b <- parametersGen
    } yield (a, b)

  val stepConfigGen: Gen[StepConfig] = Gen
    .chooseNum(0, 3)
    .flatMap(s => Gen.mapOfN[SystemName, Parameters](s, stepConfigG))

  given stParams: Cogen[StepConfig] =
    Cogen[String].contramap(_.mkString(","))

  private val perturbations: List[String => Gen[String]] =
    List(s => if (s.startsWith("-")) Gen.const(s) else Gen.const(s"00%s") // insert leading 0s
    )

  // Strings that are often parsable as Offsets
  val stringsOffsets: Gen[String] =
    arbitrary[Offset]
      .map(x => Angle.arcseconds.get(x.p.toAngle).toString)
      .flatMapOneOf(Gen.const, perturbations: _*)
}

object ArbStepConfig extends ArbStepConfig
