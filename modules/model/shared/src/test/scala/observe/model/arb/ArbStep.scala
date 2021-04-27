// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Cogen
import org.scalacheck.Gen
import observe.model._
import observe.model.arb.ArbStandardStep._
import observe.model.arb.ArbNodAndShuffleStep._

trait ArbStep {
  implicit val steArb = Arbitrary[Step] {
    for {
      ss <- arbitrary[StandardStep]
      ns <- arbitrary[NodAndShuffleStep]
      s  <- Gen.oneOf(ss, ns)
    } yield s
  }

  implicit val stepCogen: Cogen[Step] =
    Cogen[Either[StandardStep, NodAndShuffleStep]].contramap {
      case a: StandardStep      => Left(a)
      case a: NodAndShuffleStep => Right(a)
    }
}

object ArbStep extends ArbStep
