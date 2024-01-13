// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import observe.model.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

import ArbNodAndShuffleStep.given
import ArbStandardStep.given

trait ArbStep {
  given steArb: Arbitrary[Step] = Arbitrary[Step] {
    for {
      ss <- arbitrary[Step.Standard]
      ns <- arbitrary[Step.NodAndShuffle]
      s  <- Gen.oneOf(ss, ns)
    } yield s
  }

  given stepCogen: Cogen[Step] =
    Cogen[Either[Step.Standard, Step.NodAndShuffle]].contramap {
      case a: Step.Standard      => Left(a)
      case a: Step.NodAndShuffle => Right(a)
    }
}

object ArbStep extends ArbStep
