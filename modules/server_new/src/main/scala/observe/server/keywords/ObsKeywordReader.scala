// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.ObserveClass
import lucuma.core.enums.Site
import lucuma.core.enums.StepGuideState
import lucuma.core.enums.StepGuideState.Disabled
import lucuma.core.enums.StepGuideState.Enabled
import lucuma.core.model.TimingWindowEnd
import lucuma.core.model.TimingWindowRepeat
import lucuma.core.model.sequence.Step as OcsStep
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.util.TimeSpan
import lucuma.schemas.ObservationDB.Enums.GuideProbe
import mouse.all.*
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation
import observe.server.Systems

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import ConditionOps.*

sealed trait ObsKeywordsReader[F[_]] {
  def obsType: F[String]
  def obsClass: F[String]
  def gemPrgId: F[String]
  def obsId: F[String]
  def dataLabel: F[String]
  def observatory: F[String]
  def telescope: F[String]
  def pwfs1Guide: F[Option[StepGuideState]]
  def pwfs1GuideS: F[String]
  def pwfs2Guide: F[Option[StepGuideState]]
  def pwfs2GuideS: F[String]
  def oiwfsGuide: F[Option[StepGuideState]]
  def oiwfsGuideS: F[String]
  def aowfsGuide: F[Option[StepGuideState]]
  def aowfsGuideS: F[String]
  def cwfs1Guide: F[Option[StepGuideState]]
  def cwfs2Guide: F[Option[StepGuideState]]
  def cwfs3Guide: F[Option[StepGuideState]]
  def odgw1Guide: F[Option[StepGuideState]]
  def odgw2Guide: F[Option[StepGuideState]]
  def odgw3Guide: F[Option[StepGuideState]]
  def odgw4Guide: F[Option[StepGuideState]]
  def headerPrivacy: F[Boolean]
  def releaseDate: F[String]
  def obsObject: F[String]
  def geminiQA: F[String]
  def pIReq: F[String]
  def sciBand: F[Int]
  def timingWindows: F[List[(Int, TimingWindowKeywords)]]
  def requestedConditions: ConstraintSetReader[F]
  def astrometicField: F[Boolean]
}

// A Timing window always has 4 keywords
final case class TimingWindowKeywords(
  start:    String,
  duration: Double,
  repeat:   Int,
  period:   Double
)

object ObsKeywordReader {
  def apply[F[_]: Sync, D <: DynamicConfig](
    obsCfg: Observation,
    step:   OcsStep[D],
    site:   Site,
    sys:    Systems[F]
  ): ObsKeywordsReader[F] =
    new ObsKeywordsReader[F] {
      // Format used on FITS keywords
      val telescopeName: String = site match {
        case Site.GN => "Gemini-North"
        case Site.GS => "Gemini-South"
      }

      override def obsType: F[String] = (
        step.stepConfig match {
          case StepConfig.Bias             => "BIAS"
          case StepConfig.Dark             => "DARK"
          case StepConfig.Gcal(_, _, _, _) => "FLAT"
          case StepConfig.Science          => "OBJECT"
          case StepConfig.SmartGcal(_)     => "FLAT"
        }
      ).pure[F]

      override def obsClass: F[String] = (step.observeClass match {
        case ObserveClass.Science        => "science"
        case ObserveClass.ProgramCal     => "progCal"
        case ObserveClass.PartnerCal     => "partnerCal"
        case ObserveClass.Acquisition    => "acq"
        case ObserveClass.AcquisitionCal => "acqCal"
        case ObserveClass.DayCal         => "dayCal"
      }).pure[F]

      override def gemPrgId: F[String] = obsCfg.program.name.map(_.toString).getOrElse("").pure[F]

      override def obsId: F[String] = obsCfg.title.value.pure[F]

      override def requestedConditions: ConstraintSetReader[F] = ConstraintSetReader.apply(
        obsCfg.constraintSet,
        sys.conditionSetReader(obsCfg.constraintSet.asConditions)
      )

      override def timingWindows: F[List[(Int, TimingWindowKeywords)]] =
        obsCfg.timingWindows.zipWithIndex
          .map { case (w, i) =>
            i -> TimingWindowKeywords(
              w.start.toString,
              w.duration.map(_.toSeconds.toDouble).getOrElse(0.0),
              w.end
                .flatMap(TimingWindowEnd.after.andThen(TimingWindowEnd.After.repeat).getOption)
                .flatten
                .flatMap(_.times)
                .map(_.value)
                .getOrElse(1),
              w.end
                .flatMap(TimingWindowEnd.after.andThen(TimingWindowEnd.After.repeat).getOption)
                .flatten
                .map(_.period.toSeconds.toDouble)
                .getOrElse(0.0)
            )
          }
          .pure[F]

      override def dataLabel: F[String] = s"${obsCfg.id}-${step.id}".pure[F]

      override def observatory: F[String] = telescopeName.pure[F]

      override def telescope: F[String] = telescopeName.pure[F]

      private def decodeGuide(v: Option[StepGuideState]): String = v
        .map {
          case Enabled  => "guiding"
          case Disabled => "frozen"
        }
        .getOrElse("frozen")

      override def pwfs1Guide: F[Option[StepGuideState]] =
        obsCfg.targetEnvironment.guideEnvironment.guideTargets
          .exists(_.probe === GuideProbe.Pwfs1)
          .option(step.telescopeConfig.guiding)
          .pure[F]

      override def pwfs1GuideS: F[String] =
        pwfs1Guide
          .map(decodeGuide)

      override def pwfs2Guide: F[Option[StepGuideState]] =
        obsCfg.targetEnvironment.guideEnvironment.guideTargets
          .exists(_.probe === GuideProbe.Pwfs2)
          .option(step.telescopeConfig.guiding)
          .pure[F]

      override def pwfs2GuideS: F[String] =
        pwfs2Guide
          .map(decodeGuide)

      override def oiwfsGuide: F[Option[StepGuideState]] =
        obsCfg.targetEnvironment.guideEnvironment.guideTargets
          .exists(_.probe === GuideProbe.GmosOiwfs)
          .option(step.telescopeConfig.guiding)
          .pure[F]

      override def oiwfsGuideS: F[String] =
        oiwfsGuide
          .map(decodeGuide)

      override def aowfsGuide: F[Option[StepGuideState]] = none.pure[F]

      override def aowfsGuideS: F[String] =
        aowfsGuide
          .map(decodeGuide)

      override def cwfs1Guide: F[Option[StepGuideState]] = none.pure[F]

      override def cwfs2Guide: F[Option[StepGuideState]] = none.pure[F]

      override def cwfs3Guide: F[Option[StepGuideState]] = none.pure[F]

      override def odgw1Guide: F[Option[StepGuideState]] = none.pure[F]

      override def odgw2Guide: F[Option[StepGuideState]] = none.pure[F]

      override def odgw3Guide: F[Option[StepGuideState]] = none.pure[F]

      override def odgw4Guide: F[Option[StepGuideState]] = none.pure[F]

      override def headerPrivacy: F[Boolean] = false.pure[F]

      private val calcReleaseDate: F[String] = Sync[F].delay(
        LocalDate
          .now(ZoneId.of("GMT"))
          .format(DateTimeFormatter.ISO_LOCAL_DATE)
      )

      override def releaseDate: F[String] = calcReleaseDate

      override def obsObject: F[String] = (step.stepConfig match {
        case StepConfig.Bias                => "Bias"
        case StepConfig.Dark                => "Dark"
        case StepConfig.Gcal(lamp, _, _, _) =>
          lamp.toEither.fold[String](
            _ => "GCALflat",
            _.toList.mkString("", ",", "")
          )
        case _                              => ""
      }).pure[F]

      override def geminiQA: F[String] = "UNKNOWN".pure[F]

      override def pIReq: F[String] = "UNKNOWN".pure[F]

      override def sciBand: F[Int] = 1.pure[F]

      def astrometicField: F[Boolean] = false.pure[F]

    }
}
