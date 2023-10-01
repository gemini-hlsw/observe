// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.util.arb.ArbEnumerated.*
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*
import observe.model.Observation
import observe.model.ObserveStage.given
import observe.model.*
import observe.model.arb.ArbNSSubexposure.given
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen
import observe.model.arb.ArbNsSubexposure.given
import observe.model.*
import observe.model.ObserveStage.given
import scala.concurrent.duration.FiniteDuration

trait ArbObservationProgress {

  given arbObservationProgress: Arbitrary[ObservationProgress] =
    Arbitrary {
      for {
        o <- arbitrary[Observation.Id]
        s <- arbitrary[StepId]
        t <- arbitrary[FiniteDuration]
        r <- arbitrary[FiniteDuration]
        v <- arbitrary[ObserveStage]
      } yield ObservationProgress(o, s, t, r, v)
    }

  given observationInProgressCogen: Cogen[ObservationProgress] =
    Cogen[(Observation.Id, StepId, FiniteDuration, FiniteDuration, ObserveStage)]
      .contramap(x => (x.obsId, x.stepId, x.total, x.remaining, x.stage))

  given arbNSObservationProgress: Arbitrary[NSObservationProgress] =
    Arbitrary {
      for {
        o <- arbitrary[Observation.Id]
        s <- arbitrary[StepId]
        t <- arbitrary[FiniteDuration]
        r <- arbitrary[FiniteDuration]
        v <- arbitrary[ObserveStage]
        u <- arbitrary[NsSubexposure]
      } yield NSObservationProgress(o, s, t, r, v, u)
    }

  given nsObservationInProgressCogen: Cogen[NSObservationProgress] =
    Cogen[(Observation.Id, StepId, FiniteDuration, FiniteDuration, ObserveStage, NsSubexposure)]
      .contramap(x => (x.obsId, x.stepId, x.total, x.remaining, x.stage, x.sub))

  given arbProgress: Arbitrary[Progress] =
    Arbitrary {
      for {
        o <- arbitrary[ObservationProgress]
        n <- arbitrary[NSObservationProgress]
        p <- Gen.oneOf(o, n)
      } yield p
    }

  given progressCogen: Cogen[Progress] =
    Cogen[Either[ObservationProgress, NSObservationProgress]]
      .contramap {
        case x: ObservationProgress   => Left(x)
        case x: NSObservationProgress => Right(x)
      }

}

object ArbObservationProgress extends ArbObservationProgress
