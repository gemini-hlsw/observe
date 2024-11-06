// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import cats.Order.given
import cats.derived.*
import cats.syntax.all.*
import eu.timepit.refined.cats.given
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Decoder
import io.circe.HCursor
import io.circe.generic.semiauto.*
import io.circe.refined.given
import lucuma.core.enums.Instrument
import lucuma.core.model.ConstraintSet
import lucuma.core.model.ObsAttachment
import lucuma.core.model.Observation
import lucuma.core.model.ObservationReference
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

case class ObsSummary(
  obsId:              Observation.Id,
  title:              String,
  subtitle:           Option[NonEmptyString],
  instrument:         Instrument,
  constraints:        ConstraintSet,
  timingWindows:      List[TimingWindow],
  attachmentIds:      SortedSet[ObsAttachment.Id],
  observingMode:      Option[ObservingMode],
  observationTime:    Option[Instant],
  posAngleConstraint: PosAngleConstraint,
  obsReference:       Option[ObservationReference]
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
  val obsId              = Focus[ObsSummary](_.obsId)
  val title              = Focus[ObsSummary](_.title)
  val subtitle           = Focus[ObsSummary](_.subtitle)
  val instrument         = Focus[ObsSummary](_.instrument)
  val constraints        = Focus[ObsSummary](_.constraints)
  val timingWindows      = Focus[ObsSummary](_.timingWindows)
  val attachmentIds      = Focus[ObsSummary](_.attachmentIds)
  val observingMode      = Focus[ObsSummary](_.observingMode)
  val observationTime    = Focus[ObsSummary](_.observationTime)
  val posAngleConstraint = Focus[ObsSummary](_.posAngleConstraint)
  val obsReference       = Focus[ObsSummary](_.obsReference)

  private case class AttachmentIdWrapper(id: ObsAttachment.Id)
  private object AttachmentIdWrapper:
    given Decoder[AttachmentIdWrapper] = deriveDecoder

  given Decoder[ObsSummary] = Decoder.instance: c =>
    for
      id                 <- c.get[Observation.Id]("id")
      title              <- c.get[String]("title")
      subtitle           <- c.get[Option[NonEmptyString]]("subtitle")
      instrument         <- c.get[Option[Instrument]]("instrument")
      constraints        <- c.get[ConstraintSet]("constraintSet")
      timingWindows      <- c.get[List[TimingWindow]]("timingWindows")
      attachmentIds      <- c.get[List[AttachmentIdWrapper]]("obsAttachments")
      observingMode      <- c.get[Option[ObservingMode]]("observingMode")
      observationTime    <- c.get[Option[Timestamp]]("observationTime")
      posAngleConstraint <- c.get[PosAngleConstraint]("posAngleConstraint")
      obsReference       <-
        c.get[Option[HCursor]]("reference")
          .map(_.map(_.get[Option[ObservationReference]]("label")).sequence.map(_.flatten))
          .sequence
          .flatten
    yield ObsSummary(
      id,
      title,
      subtitle,
      instrument.getOrElse(Instrument.Visitor),
      constraints,
      timingWindows,
      SortedSet.from(attachmentIds.map(_.id)),
      observingMode,
      observationTime.map(_.toInstant),
      posAngleConstraint,
      obsReference
    )
