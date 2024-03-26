// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.model.sequence.Step
import lucuma.core.util.TimeSpan
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbTimeSpan.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.*
import observe.model.ObserveStage.given
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

import ArbNsSubexposure.given

trait ArbStepProgress:
  given Arbitrary[StepProgress.Regular] =
    Arbitrary:
      for
        s <- arbitrary[Step.Id]
        t <- arbitrary[TimeSpan]
        r <- arbitrary[TimeSpan]
        v <- arbitrary[ObserveStage]
      yield StepProgress.Regular(s, t, r, v)

  given Cogen[StepProgress.Regular] =
    Cogen[(Step.Id, TimeSpan, TimeSpan, ObserveStage)]
      .contramap(x => (x.stepId, x.total, x.remaining, x.stage))

  given Arbitrary[StepProgress.NodAndShuffle] =
    Arbitrary:
      for
        s <- arbitrary[Step.Id]
        t <- arbitrary[TimeSpan]
        r <- arbitrary[TimeSpan]
        v <- arbitrary[ObserveStage]
        u <- arbitrary[NsSubexposure]
      yield StepProgress.NodAndShuffle(s, t, r, v, u)

  given Cogen[StepProgress.NodAndShuffle] =
    Cogen[(Step.Id, TimeSpan, TimeSpan, ObserveStage, NsSubexposure)]
      .contramap(x => (x.stepId, x.total, x.remaining, x.stage, x.sub))

  given Arbitrary[StepProgress] =
    Arbitrary:
      Gen.oneOf(arbitrary[StepProgress.Regular], arbitrary[StepProgress.NodAndShuffle])

  given Cogen[StepProgress] =
    Cogen[Either[StepProgress.Regular, StepProgress.NodAndShuffle]]
      .contramap:
        case x @ StepProgress.Regular(_, _, _, _)          => Left(x)
        case x @ StepProgress.NodAndShuffle(_, _, _, _, _) => Right(x)

object ArbStepProgress extends ArbStepProgress
