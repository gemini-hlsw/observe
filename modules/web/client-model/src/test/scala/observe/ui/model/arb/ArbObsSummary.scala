// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.arb

import cats.Order.given
import lucuma.core.model.ConstraintSet
import lucuma.core.model.ObsAttachment
import lucuma.core.model.PosAngleConstraint
import lucuma.core.model.TimingWindow
import lucuma.core.model.arb.ArbConstraintSet.given
import lucuma.core.model.arb.ArbPosAngleConstraint.given
import lucuma.core.model.arb.ArbTimingWindow.given
import lucuma.core.util.arb.ArbGid.given
import lucuma.schemas.model.ObservingMode
import lucuma.schemas.model.arb.ArbObservingMode.given
import observe.ui.model.ObsSummary
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Cogen

import java.time.Instant
import scala.collection.immutable.SortedSet

trait ArbObsSummary:
  given Arbitrary[ObsSummary] = Arbitrary:
    for
      title              <- arbitrary[String]
      constraints        <- arbitrary[ConstraintSet]
      timingWindows      <- arbitrary[List[TimingWindow]]
      attachmentIds      <- arbitrary[List[ObsAttachment.Id]]
      observingMode      <- arbitrary[Option[ObservingMode]]
      visualizationTime  <- arbitrary[Option[Instant]]
      posAngleConstraint <- arbitrary[PosAngleConstraint]
    yield ObsSummary(
      title,
      constraints,
      timingWindows,
      SortedSet.from(attachmentIds),
      observingMode,
      visualizationTime,
      posAngleConstraint
    )

  given Cogen[ObsSummary] =
    Cogen[
      (String,
       ConstraintSet,
       List[TimingWindow],
       List[ObsAttachment.Id],
       Option[ObservingMode],
       Option[Instant],
       PosAngleConstraint
      )
    ]
      .contramap: s =>
        (s.title,
         s.constraints,
         s.timingWindows,
         s.attachmentIds.toList,
         s.observingMode,
         s.visualizationTime,
         s.posAngleConstraint
        )

object ArbObsSummary extends ArbObsSummary