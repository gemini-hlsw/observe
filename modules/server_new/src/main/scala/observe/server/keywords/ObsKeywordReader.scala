// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import cats.Eq
import cats.data.{EitherT, Nested}
import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.{GuideState, ObserveClass, Site}
import lucuma.core.model.sequence.{StepConfig, Step as OcsStep}
import lucuma.core.enums.GuideState.{Disabled, Enabled}
import lucuma.core.model.{ElevationRange, TimingWindowEnd, TimingWindowRepeat}
import lucuma.core.syntax.string.*
import mouse.boolean.*
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation
import observe.server.tcs.Tcs

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}

sealed trait ObsKeywordsReader[F[_]] {
  def obsType: F[String]
  def obsClass: F[String]
  def gemPrgId: F[String]
  def obsId: F[String]
  def dataLabel: F[String]
  def observatory: F[String]
  def telescope: F[String]
  def pwfs1Guide: F[Option[GuideState]]
  def pwfs1GuideS: F[String]
  def pwfs2Guide: F[Option[GuideState]]
  def pwfs2GuideS: F[String]
  def oiwfsGuide: F[Option[GuideState]]
  def oiwfsGuideS: F[String]
  def aowfsGuide: F[Option[GuideState]]
  def aowfsGuideS: F[String]
  def cwfs1Guide: F[Option[GuideState]]
  def cwfs2Guide: F[Option[GuideState]]
  def cwfs3Guide: F[Option[GuideState]]
  def odgw1Guide: F[Option[GuideState]]
  def odgw2Guide: F[Option[GuideState]]
  def odgw3Guide: F[Option[GuideState]]
  def odgw4Guide: F[Option[GuideState]]
  def headerPrivacy: F[Boolean]
  def releaseDate: F[String]
  def obsObject: F[String]
  def geminiQA: F[String]
  def pIReq: F[String]
  def sciBand: F[Int]
  def requestedAirMassAngle: F[Map[String, Double]]
  def timingWindows: F[List[(Int, TimingWindowKeywords)]]
  def requestedConditions: F[Map[String, String]]
  def astrometicField: F[Boolean]
}

trait ObsKeywordsReaderConstants {
  // Constants taken from SPSiteQualityCB
  // TODO Make them public in SPSiteQualityCB
  val MIN_HOUR_ANGLE: String = "MinHourAngle"
  val MAX_HOUR_ANGLE: String = "MaxHourAngle"
  val MIN_AIRMASS: String    = "MinAirmass"
  val MAX_AIRMASS: String    = "MaxAirmass"

  val TIMING_WINDOW_START: String    = "TimingWindowStart"
  val TIMING_WINDOW_DURATION: String = "TimingWindowDuration"
  val TIMING_WINDOW_REPEAT: String   = "TimingWindowRepeat"
  val TIMING_WINDOW_PERIOD: String   = "TimingWindowPeriod"

  val SB: String = "SkyBackground"
  val CC: String = "CloudCover"
  val IQ: String = "ImageQuality"
  val WV: String = "WaterVapor"
}

// A Timing window always has 4 keywords
final case class TimingWindowKeywords(
  start:    String,
  duration: Double,
  repeat:   Int,
  period:   Double
)

object ObsKeywordReader extends ObsKeywordsReaderConstants {
  def apply[F[_]: Sync, D <: Dynamic](obsCfg: Observation, step: OcsStep[D], site: Site): ObsKeywordsReader[F] =
    new ObsKeywordsReader[F] {
      // Format used on FITS keywords
      val telescopeName: String = site match {
        case Site.GN => "Gemini-North"
        case Site.GS => "Gemini-South"
      }

      override def obsType: F[String] = (
        step.stepConfig match {
          case StepConfig.Bias => "BIAS"
          case StepConfig.Dark => "DARK"
          case StepConfig.Gcal(lamp, filter, diffuser, shutter) => "FLAT"
          case StepConfig.Science(offset, guiding) => "OBJECT"
          case StepConfig.SmartGcal(smartGcalType) => "FLAT"
        }
      ).pure[F]

      override def obsClass: F[String] = step.observeClass.tag.pure[F]

      override def gemPrgId: F[String] = obsCfg.program.name.map(_.toString).getOrElse("").pure[F]

      override def obsId: F[String] = obsCfg.title.pure[F]

      override def requestedAirMassAngle: F[Map[String, Double]] = obsCfg.constraintSet.elevationRange match {
        case ElevationRange.AirMass(min, max) => Map(
          MAX_AIRMASS -> max.value.toDouble,
          MIN_AIRMASS -> min.value.toDouble
        ).pure[F]
        case ElevationRange.HourAngle(minHours, maxHours) => Map(
          MAX_HOUR_ANGLE -> maxHours.value.toDouble,
          MIN_HOUR_ANGLE -> minHours.value.toDouble
        ).pure[F]
      }

      override def requestedConditions: F[Map[String, String]] = Map(
        SB -> obsCfg.constraintSet.skyBackground.label,
        CC -> obsCfg.constraintSet.cloudExtinction.label,
        IQ -> obsCfg.constraintSet.imageQuality.label,
        WV -> obsCfg.constraintSet.waterVapor.label
      ).pure[F]

      override def timingWindows: F[List[(Int, TimingWindowKeywords)]] = obsCfg.timingWindows.zipWithIndex.map{
        case (w, i) => i -> TimingWindowKeywords(
          w.start.toString,
          w.duration.map(_.toSeconds.toDouble).getOrElse(0.0),
          w.end.flatMap(TimingWindowEnd.after.andThen(TimingWindowEnd.After.repeat).getOption).flatten.flatMap(_.times).map(_.value).getOrElse(1),
          w.end.flatMap(TimingWindowEnd.after.andThen(TimingWindowEnd.After.repeat).getOption).flatten.map(_.period.toSeconds.toDouble).getOrElse(0.0),
        )
      }.pure[F]

      override def dataLabel: F[String] = s"${obsCfg.id}-${step.id}".pure[F]

      override def observatory: F[String] = telescopeName.pure[F]

      override def telescope: F[String] = telescopeName.pure[F]

      private def decodeGuide(v: Option[GuideState]): String = v.map{
        case Enabled => "guiding"
        case Disabled => "frozen"
      }.getOrElse("frozen")

      override def pwfs1Guide: F[Option[GuideState]] = none.pure[F]

      override def pwfs1GuideS: F[String] =
        pwfs1Guide
          .map(decodeGuide)

      override def pwfs2Guide: F[Option[GuideState]] = none.pure[F]

      override def pwfs2GuideS: F[String] =
        pwfs2Guide
          .map(decodeGuide)

      override def oiwfsGuide: F[Option[GuideState]] =  none.pure[F]

      override def oiwfsGuideS: F[String] =
        oiwfsGuide
          .map(decodeGuide)

      override def aowfsGuide: F[Option[GuideState]] = none.pure[F]

      override def aowfsGuideS: F[String] =
        aowfsGuide
          .map(decodeGuide)

      override def cwfs1Guide: F[Option[GuideState]] = none.pure[F]

      override def cwfs2Guide: F[Option[GuideState]] = none.pure[F]

      override def cwfs3Guide: F[Option[GuideState]] = none.pure[F]

      override def odgw1Guide: F[Option[GuideState]] = none.pure[F]

      override def odgw2Guide: F[Option[GuideState]] = none.pure[F]

      override def odgw3Guide: F[Option[GuideState]] = none.pure[F]

      override def odgw4Guide: F[Option[GuideState]] = none.pure[F]

      override def headerPrivacy: F[Boolean] = false.pure[F]

      private val calcReleaseDate: F[String] = Sync[F].delay(
        LocalDate
          .now(ZoneId.of("GMT"))
          .format(DateTimeFormatter.ISO_LOCAL_DATE)
      )

      override def releaseDate: F[String] = calcReleaseDate

      private val manualDarkValue = "Manual Dark"
      private val manualDarkOverride = "Dark"

      override def obsObject: F[String] = "".pure[F]

      override def geminiQA: F[String] = "UNKNOWN".pure[F]

      override def pIReq: F[String] = "UNKNOWN".pure[F]

      override def sciBand: F[Int] = 1.pure[F]

      def astrometicField: F[Boolean] = false.pure[F]

    }
}
