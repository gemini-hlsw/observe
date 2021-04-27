// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen
import observe.model.RunningStep
import observe.model.StepId

trait ArbRunningStep {

  implicit val arbRunningStep: Arbitrary[RunningStep] =
    Arbitrary {
      for {
        l <- Gen.posNum[Int]
        i <- Gen.choose(l, Int.MaxValue)
      } yield RunningStep.fromInt(l, i).getOrElse(RunningStep.Zero)
    }

  implicit val runningStepCogen: Cogen[RunningStep] =
    Cogen[(StepId, StepId)].contramap(x => (x.last, x.total))

}

object ArbRunningStep extends ArbRunningStep
