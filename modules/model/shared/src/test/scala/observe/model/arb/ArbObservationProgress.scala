// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.util.TimeSpan
import lucuma.core.util.arb.ArbEnumerated.*
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbTimeSpan.given
import lucuma.core.util.arb.ArbUid.*
import observe.model.Observation
import observe.model.ObserveStage.given
import observe.model.*
import observe.model.arb.ArbNsSubexposure.given
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait ArbObservationProgress:
  given Arbitrary[ObservationProgress.Regular] =
    Arbitrary:
      for
        o <- arbitrary[Observation.Id]
        s <- arbitrary[StepId]
        t <- arbitrary[TimeSpan]
        r <- arbitrary[TimeSpan]
        v <- arbitrary[ObserveStage]
      yield ObservationProgress.Regular(o, s, t, r, v)

  given Cogen[ObservationProgress.Regular] =
    Cogen[(Observation.Id, StepId, TimeSpan, TimeSpan, ObserveStage)]
      .contramap(x => (x.obsId, x.stepId, x.total, x.remaining, x.stage))

  given Arbitrary[ObservationProgress.NodAndShuffle] =
    Arbitrary:
      for
        o <- arbitrary[Observation.Id]
        s <- arbitrary[StepId]
        t <- arbitrary[TimeSpan]
        r <- arbitrary[TimeSpan]
        v <- arbitrary[ObserveStage]
        u <- arbitrary[NsSubexposure]
      yield ObservationProgress.NodAndShuffle(o, s, t, r, v, u)

  given Cogen[ObservationProgress.NodAndShuffle] =
    Cogen[(Observation.Id, StepId, TimeSpan, TimeSpan, ObserveStage, NsSubexposure)]
      .contramap(x => (x.obsId, x.stepId, x.total, x.remaining, x.stage, x.sub))

  given Arbitrary[ObservationProgress] =
    Arbitrary:
      for
        o <- arbitrary[ObservationProgress.Regular]
        n <- arbitrary[ObservationProgress.NodAndShuffle]
        p <- Gen.oneOf(o, n)
      yield p

  given Cogen[ObservationProgress] =
    Cogen[Either[ObservationProgress.Regular, ObservationProgress.NodAndShuffle]]
      .contramap:
        case x @ ObservationProgress.Regular(_, _, _, _, _)          => Left(x)
        case x @ ObservationProgress.NodAndShuffle(_, _, _, _, _, _) => Right(x)

object ArbObservationProgress extends ArbObservationProgress
