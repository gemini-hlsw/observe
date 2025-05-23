// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import observe.model.StepState
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait ArbStepState {
  given stepStateArb: Arbitrary[StepState] = Arbitrary[StepState] {
    for {
      v1 <- Gen.oneOf(StepState.Pending,
                      StepState.Completed,
                      StepState.Aborted,
                      StepState.Running,
                      StepState.Paused
            )
      v2 <- Gen.alphaStr.map(StepState.Failed.apply)
      r  <- Gen.oneOf(v1, v2)
    } yield r
  }

  given stepStateCogen: Cogen[StepState] =
    Cogen[String].contramap(_.productPrefix)

}

object ArbStepState extends ArbStepState
