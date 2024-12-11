// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.arb

import eu.timepit.refined.scalacheck.string.given
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.enums.Instrument
import lucuma.core.model.ConstraintSet
import lucuma.core.model.Observation
import lucuma.core.model.ObservationReference
import lucuma.core.model.PosAngleConstraint
import lucuma.core.model.TimingWindow
import lucuma.core.model.arb.ArbConstraintSet.given
import lucuma.core.model.arb.ArbObservationReference.given
import lucuma.core.model.arb.ArbPosAngleConstraint.given
import lucuma.core.model.arb.ArbTimingWindow.given
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbGid.given
import lucuma.schemas.model.ObservingMode
import lucuma.schemas.model.arb.ArbObservingMode.given
import observe.ui.model.ObsSummary
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Cogen

import java.time.Instant

trait ArbObsSummary:
  given Arbitrary[ObsSummary] = Arbitrary:
    for
      obsId              <- arbitrary[Observation.Id]
      title              <- arbitrary[String]
      subtitle           <- arbitrary[Option[NonEmptyString]]
      instrument         <- arbitrary[Instrument]
      constraints        <- arbitrary[ConstraintSet]
      timingWindows      <- arbitrary[List[TimingWindow]]
      observingMode      <- arbitrary[Option[ObservingMode]]
      observationTime    <- arbitrary[Option[Instant]]
      posAngleConstraint <- arbitrary[PosAngleConstraint]
      obsReference       <- arbitrary[Option[ObservationReference]]
    yield ObsSummary(
      obsId,
      title,
      subtitle,
      instrument,
      constraints,
      timingWindows,
      observingMode,
      observationTime,
      posAngleConstraint,
      obsReference
    )

  given Cogen[ObsSummary] =
    Cogen[
      (Observation.Id,
       String,
       Option[String],
       Instrument,
       ConstraintSet,
       List[TimingWindow],
       Option[ObservingMode],
       Option[Instant],
       PosAngleConstraint,
       Option[ObservationReference]
      )
    ]
      .contramap: s =>
        (s.obsId,
         s.title,
         s.subtitle.map(_.value),
         s.instrument,
         s.constraints,
         s.timingWindows,
         s.observingMode,
         s.observationTime,
         s.posAngleConstraint,
         s.obsReference
        )

object ArbObsSummary extends ArbObsSummary
