// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import cats.Applicative
import cats.Monad
import cats.syntax.all.*
import lucuma.core.enums.Site
import lucuma.core.math.Angle
import lucuma.core.math.Wavelength
import observe.model.Conditions
import observe.server.tcs.TcsEpics

import ConditionOps.*

trait ConditionSetReader[F[_]] {
  def imageQualityStr: F[String]

  def cloudExtinctionStr: F[String]

  def waterVaporStr: F[String]

  def backgroundLightStr: F[String]

  def imageQualityDbl: F[Double]

  def cloudExtinctionDbl: F[Double]

  def waterVaporDbl: F[Double]

  def backgroundLightDbl: F[Double]
}

object ConditionSetReaderEpics {
  def apply[F[_]: Monad](site: Site, tcsEpics: TcsEpics[F])(
    conditions: Conditions
  ): ConditionSetReader[F] = new ConditionSetReader[F]:
    private def percentileStr(v: Option[Int]): String =
      v.map(x => if (x === 100) "Any" else s"$x-percentile").getOrElse("UNKNOWN")

    override def imageQualityStr: F[String] = for {
      wv <- tcsEpics.sourceATarget.centralWavelenght
      el <- tcsEpics.elevation
    } yield percentileStr(
      Wavelength
        .fromIntNanometers(wv.toInt)
        .flatMap(w => conditions.iq.map(_.toPercentile(w, Angle.fromDoubleDegrees(el))))
    )

    override def cloudExtinctionStr: F[String] =
      percentileStr(conditions.ce.map(_.toPercentile)).pure[F]

    override def waterVaporStr: F[String] = percentileStr(conditions.wv.map(_.toPercentile)).pure[F]

    override def backgroundLightStr: F[String] =
      percentileStr(conditions.sb.map(_.toPercentile)).pure[F]

    override def imageQualityDbl: F[Double] = conditions.iq
      .map(_.toArcSeconds.value.toDouble)
      .getOrElse(DefaultHeaderValue[Double].default)
      .pure[F]

    override def cloudExtinctionDbl: F[Double] =
      conditions.ce.map(_.toBrightness).getOrElse(DefaultHeaderValue[Double].default).pure[F]

    override def waterVaporDbl: F[Double] =
      conditions.wv.map(_.toMillimeters(site)).getOrElse(DefaultHeaderValue[Double].default).pure[F]

    override def backgroundLightDbl: F[Double] =
      conditions.sb.map(_.toMicroVolts).getOrElse(DefaultHeaderValue[Double].default).pure[F]
}

object DummyConditionSetReader {
  def apply[F[_]: Applicative](site: Site)(conditions: Conditions): ConditionSetReader[F] =
    new ConditionSetReader[F] {
      private def percentileStr(v: Option[Int]): String =
        v.map(x => if (x === 100) "Any" else s"$x-percentile").getOrElse("UNKNOWN")

      override def imageQualityStr: F[String] = DefaultHeaderValue[String].default.pure[F]

      override def cloudExtinctionStr: F[String] =
        percentileStr(conditions.ce.map(_.toPercentile)).pure[F]

      override def waterVaporStr: F[String] =
        percentileStr(conditions.wv.map(_.toPercentile)).pure[F]

      override def backgroundLightStr: F[String] =
        percentileStr(conditions.sb.map(_.toPercentile)).pure[F]

      override def imageQualityDbl: F[Double] = conditions.iq
        .map(_.toArcSeconds.value.toDouble)
        .getOrElse(DefaultHeaderValue[Double].default)
        .pure[F]

      override def cloudExtinctionDbl: F[Double] =
        conditions.ce.map(_.toBrightness).getOrElse(DefaultHeaderValue[Double].default).pure[F]

      override def waterVaporDbl: F[Double] = conditions.wv
        .map(_.toMillimeters(site))
        .getOrElse(DefaultHeaderValue[Double].default)
        .pure[F]

      override def backgroundLightDbl: F[Double] =
        conditions.sb.map(_.toMicroVolts).getOrElse(DefaultHeaderValue[Double].default).pure[F]
    }
}
