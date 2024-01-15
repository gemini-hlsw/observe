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
  given steArb: Arbitrary[ObserveStep] = Arbitrary[ObserveStep] {
    for {
      ss <- arbitrary[StandardStep]
      ns <- arbitrary[NodAndShuffleStep]
      s  <- Gen.oneOf(ss, ns)
    } yield s
  }

  given stepCogen: Cogen[ObserveStep] =
    Cogen[Either[StandardStep, NodAndShuffleStep]].contramap {
      case a: StandardStep      => Left(a)
      case a: NodAndShuffleStep => Right(a)
    }
}

object ArbStep extends ArbStep
