// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.arb

import cats.syntax.all.*
import crystal.Pot
import crystal.arb.given
import lucuma.core.model.Observation
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.model.sequence.arb.ArbInstrumentExecutionConfig.given
import lucuma.core.util.arb.ArbGid.given
import observe.ui.model.LoadedObservation
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Cogen

trait ArbLoadedObservation:
  given Arbitrary[LoadedObservation] = Arbitrary:
    for
      obsId      <- arbitrary[Observation.Id]
      refreshing <- arbitrary[Boolean]
      errorMsg   <- arbitrary[Option[String]]
      config     <- arbitrary[Pot[InstrumentExecutionConfig]]
    yield
      val base = LoadedObservation(obsId)
      (LoadedObservation.refreshing.replace(refreshing) >>>
        LoadedObservation.errorMsg.replace(errorMsg))(
        config.toOptionTry.fold(base)(t => base.withConfig(t.map(_.some).toEither))
      )

  given Cogen[LoadedObservation] =
    Cogen[(Observation.Id, Boolean, Option[String], Pot[InstrumentExecutionConfig])]
      .contramap: s =>
        (s.obsId, s.refreshing, s.errorMsg, s.config)

object ArbLoadedObservation extends ArbLoadedObservation
