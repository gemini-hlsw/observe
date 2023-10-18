// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import cats.Order.given
import cats.derived.*
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.generic.semiauto.*
import lucuma.core.model.ConstraintSet
import lucuma.core.model.ObsAttachment
// import lucuma.core.model.Observation
import lucuma.core.model.PosAngleConstraint
import lucuma.core.model.TimingWindow
import lucuma.core.util.Timestamp
import lucuma.schemas.decoders.given
import lucuma.schemas.model.BasicConfiguration
import lucuma.schemas.model.ObservingMode
import monocle.Focus
import org.typelevel.cats.time.*

import java.time.Instant
import scala.collection.immutable.SortedSet

// This class is only used by the UI, but it's in the shared model project so that it can be used
// by the UI's clue queries.
case class ObsSummary(
  // id:                 Observation.Id,
  title:              String,
  constraints:        ConstraintSet,
  timingWindows:      List[TimingWindow],
  attachmentIds:      SortedSet[ObsAttachment.Id],
  observingMode:      Option[ObservingMode],
  visualizationTime:  Option[Instant],
  posAngleConstraint: PosAngleConstraint
) derives Eq:
  lazy val configurationSummary: Option[String] = observingMode.map(_.toBasicConfiguration) match
    case Some(BasicConfiguration.GmosNorthLongSlit(grating, _, fpu, _)) =>
      s"GMOS-N ${grating.shortName} ${fpu.shortName}".some
    case Some(BasicConfiguration.GmosSouthLongSlit(grating, _, fpu, _)) =>
      s"GMOS-S ${grating.shortName} ${fpu.shortName}".some
    case _                                                              =>
      none

  lazy val constraintsSummary: String =
    s"${constraints.imageQuality.label} ${constraints.cloudExtinction.label} ${constraints.skyBackground.label} ${constraints.waterVapor.label}"

object ObsSummary:
  // val id                 = Focus[ObsSummary](_.id)
  val title              = Focus[ObsSummary](_.title)
  val constraints        = Focus[ObsSummary](_.constraints)
  val timingWindows      = Focus[ObsSummary](_.timingWindows)
  val attachmentIds      = Focus[ObsSummary](_.attachmentIds)
  val observingMode      = Focus[ObsSummary](_.observingMode)
  val visualizationTime  = Focus[ObsSummary](_.visualizationTime)
  val posAngleConstraint = Focus[ObsSummary](_.posAngleConstraint)

  private case class AttachmentIdWrapper(id: ObsAttachment.Id)
  private object AttachmentIdWrapper:
    given Decoder[AttachmentIdWrapper] = deriveDecoder

  given Decoder[ObsSummary] = Decoder.instance: c =>
    for
      // id                 <- c.get[Observation.Id]("id")
      title              <- c.get[String]("title")
      constraints        <- c.get[ConstraintSet]("constraintSet")
      timingWindows      <- c.get[List[TimingWindow]]("timingWindows")
      attachmentIds      <- c.get[List[AttachmentIdWrapper]]("obsAttachments")
      observingMode      <- c.get[Option[ObservingMode]]("observingMode")
      visualizationTime  <- c.get[Option[Timestamp]]("visualizationTime")
      posAngleConstraint <- c.get[PosAngleConstraint]("posAngleConstraint")
    yield ObsSummary(
      // id,
      title,
      constraints,
      timingWindows,
      SortedSet.from(attachmentIds.map(_.id)),
      observingMode,
      visualizationTime.map(_.toInstant),
      posAngleConstraint
    )
