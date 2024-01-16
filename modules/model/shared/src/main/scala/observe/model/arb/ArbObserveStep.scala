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

trait ArbObserveStep {
  given Arbitrary[ObserveStep] = Arbitrary[ObserveStep] {
    for {
      ss <- arbitrary[ObserveStep.Standard]
      ns <- arbitrary[ObserveStep.NodAndShuffle]
      s  <- Gen.oneOf(ss, ns)
    } yield s
  }

  given Cogen[ObserveStep] =
    Cogen[Either[ObserveStep.Standard, ObserveStep.NodAndShuffle]].contramap {
      case a: ObserveStep.Standard      => Left(a)
      case a: ObserveStep.NodAndShuffle => Right(a)
    }
}

object ArbObserveStep extends ArbObserveStep
