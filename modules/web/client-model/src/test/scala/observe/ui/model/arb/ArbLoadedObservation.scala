// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.arb

import cats.syntax.all.*
import crystal.Pot
import crystal.arb.given
import lucuma.ui.sequence.SequenceData
import lucuma.ui.sequence.arb.ArbSequenceData.given
import observe.ui.model.LoadedObservation
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Cogen

trait ArbLoadedObservation:
  given Arbitrary[LoadedObservation] = Arbitrary:
    for
      refreshing   <- arbitrary[Boolean]
      errorMsg     <- arbitrary[Option[String]]
      sequenceData <- arbitrary[Pot[SequenceData]]
    yield
      val base = LoadedObservation()
      (LoadedObservation.refreshing.replace(refreshing) >>>
        LoadedObservation.errorMsg.replace(errorMsg))(
        sequenceData.toOptionTry.fold(base)(sd => base.withSequenceData(sd.toEither))
      )

  given Cogen[LoadedObservation] =
    Cogen[(Boolean, Option[String], Pot[SequenceData])]
      .contramap: s =>
        (s.refreshing, s.errorMsg, s.sequenceData)

object ArbLoadedObservation extends ArbLoadedObservation
