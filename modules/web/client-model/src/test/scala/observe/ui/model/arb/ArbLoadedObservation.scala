// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.arb

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import observe.ui.model.LoadedObservation
import lucuma.core.model.Observation
import lucuma.core.util.arb.ArbGid.given
import crystal.Pot
import crystal.arb.given
import observe.ui.model.ObsSummary
import observe.ui.model.arb.ArbObsSummary.given
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.model.sequence.arb.ArbInstrumentExecutionConfig.given

trait ArbLoadedObservation:
  given Arbitrary[LoadedObservation] = Arbitrary:
    for {
      obsId   <- arbitrary[Observation.Id]
      summary <- arbitrary[Pot[ObsSummary]]
      config  <- arbitrary[Pot[InstrumentExecutionConfig]]
    } yield
      val base            = LoadedObservation(obsId)
      val baseWithSummary = summary.toOptionTry.fold(base)(t => base.withSummary(t.toEither))
      config.toOptionTry.fold(baseWithSummary)(t => baseWithSummary.withConfig(t.toEither))

object ArbLoadedObservation extends ArbLoadedObservation
