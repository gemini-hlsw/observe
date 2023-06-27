// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import observe.model.Observation
import lucuma.core.util.arb.ArbEnumerated.*
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen
import observe.model.arb.ArbTime.{*, given}
import observe.model.arb.ArbNSSubexposure.{*, given}
import observe.model.*
import observe.model.ObserveStage.given
import squants.time.*

trait ArbObservationProgress {
  import ArbObservationIdName.{*, given}

  given arbObservationProgress: Arbitrary[ObservationProgress] =
    Arbitrary {
      for {
        o <- arbitrary[Observation.IdName]
        s <- arbitrary[StepId]
        t <- arbitrary[Time]
        r <- arbitrary[Time]
        v <- arbitrary[ObserveStage]
      } yield ObservationProgress(o, s, t, r, v)
    }

  given observationInProgressCogen: Cogen[ObservationProgress] =
    Cogen[(Observation.IdName, StepId, Time, Time, ObserveStage)]
      .contramap(x => (x.obsIdName, x.stepId, x.total, x.remaining, x.stage))

  given arbNSObservationProgress: Arbitrary[NSObservationProgress] =
    Arbitrary {
      for {
        o <- arbitrary[Observation.IdName]
        s <- arbitrary[StepId]
        t <- arbitrary[Time]
        r <- arbitrary[Time]
        v <- arbitrary[ObserveStage]
        u <- arbitrary[NSSubexposure]
      } yield NSObservationProgress(o, s, t, r, v, u)
    }

  given nsObservationInProgressCogen: Cogen[NSObservationProgress] =
    Cogen[(Observation.IdName, StepId, Time, Time, ObserveStage, NSSubexposure)]
      .contramap(x => (x.obsIdName, x.stepId, x.total, x.remaining, x.stage, x.sub))

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
