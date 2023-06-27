// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import cats.syntax.option.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen
import observe.model.RunningStep
import observe.model.StepId
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*

trait ArbRunningStep {

  given arbRunningStep: Arbitrary[RunningStep] =
    Arbitrary {
      for {
        id <- arbitrary[StepId]
        l  <- Gen.posNum[Int]
        i  <- Gen.choose(l, Int.MaxValue)
      } yield RunningStep.fromInt(id.some, l, i).getOrElse(RunningStep.Zero)
    }

  given runningStepCogen: Cogen[RunningStep] =
    Cogen[(Option[StepId], Int, Int)].contramap(x => (x.id, x.last, x.total))

}

object ArbRunningStep extends ArbRunningStep
