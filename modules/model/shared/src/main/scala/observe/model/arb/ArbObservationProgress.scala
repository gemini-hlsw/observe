// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.util.arb.ArbGid.*
import observe.model.Observation
import observe.model.ObservationProgress
import observe.model.StepProgress
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen

import ArbStepProgress.given

trait ArbObservationProgress:
  given Arbitrary[ObservationProgress] =
    Arbitrary:
      for
        o <- arbitrary[Observation.Id]
        s <- arbitrary[StepProgress]
      yield ObservationProgress(o, s)

  given Cogen[ObservationProgress] =
    Cogen[(Observation.Id, StepProgress)]
      .contramap(x => (x.obsId, x.stepProgress))

object ArbObservationProgress extends ArbObservationProgress
