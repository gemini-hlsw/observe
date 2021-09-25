// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import cats.syntax.option._
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Cogen
import org.scalacheck.Gen
import observe.model.RunningStep
import observe.model.StepId
import lucuma.core.util.arb.ArbGid._

trait ArbRunningStep {

  implicit val arbRunningStep: Arbitrary[RunningStep] =
    Arbitrary {
      for {
        id <- arbitrary[StepId]
        l  <- Gen.posNum[Int]
        i  <- Gen.choose(l, Int.MaxValue)
      } yield RunningStep.fromInt(id.some, l, i).getOrElse(RunningStep.Zero)
    }

  implicit val runningStepCogen: Cogen[RunningStep]   =
    Cogen[(Option[StepId], Int, Int)].contramap(x => (x.id, x.last, x.total))

}

object ArbRunningStep extends ArbRunningStep
