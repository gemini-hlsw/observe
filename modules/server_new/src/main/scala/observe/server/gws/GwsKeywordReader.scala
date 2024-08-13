// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gws

import cats.Applicative
import cats.effect.Sync
import cats.syntax.all.*
import coulomb.Quantity
import coulomb.syntax.*
import coulomb.units.temperature.*
import lucuma.core.math.Angle
import lucuma.core.math.units.MetersPerSecond
import lucuma.core.util.NewType
import observe.server.EpicsHealth
import observe.server.keywords.*

object DewPoint extends NewType[Temperature[Double, Celsius]]
type DewPoint = DewPoint.Type

trait GwsKeywordReader[F[_]] {
  def health: F[EpicsHealth]

  def temperature: F[Temperature[Double, Celsius]]

  def dewPoint: F[DewPoint]

  def airPressure: F[Quantity[Double, Bar]]

  def windVelocity: F[Quantity[Double, MetersPerSecond]]

  def windDirection: F[Angle]

  def humidity: F[Double]
}

trait GwsDefaults {
  def toDewPoint(t: Temperature[Double, Celsius]): DewPoint =
    DewPoint(t)

  // Default value for quantities
  given DefaultHeaderValue[Temperature[Double, Celsius]] =
    DefaultHeaderValue[Double].map(_.withTemperature[Celsius])

  given DefaultHeaderValue[DewPoint] =
    DefaultHeaderValue[Temperature[Double, Celsius]].map(toDewPoint)

  given defaultPressure: DefaultHeaderValue[Quantity[Double, Bar]] =
    DefaultHeaderValue[Double].map(_.withUnit[Bar])

  given defaultVelocity: DefaultHeaderValue[Quantity[Double, MetersPerSecond]] =
    DefaultHeaderValue[Double].map(_.withUnit[MetersPerSecond])

  given DefaultHeaderValue[Angle] =
    DefaultHeaderValue[Double].map(Angle.fromDoubleDegrees)

}

object GwsKeywordsReaderDummy extends GwsDefaults {
  def apply[F[_]: Applicative]: GwsKeywordReader[F] = new GwsKeywordReader[F] {
    override def temperature: F[Temperature[Double, Celsius]] =
      15.0.withTemperature[Celsius].pure[F]

    override def dewPoint: F[DewPoint] =
      toDewPoint(1.0.withTemperature[Celsius]).pure[F]

    override def airPressure: F[Quantity[Double, Bar]] = 1.0.withUnit[Bar].pure[F]

    override def windVelocity: F[Quantity[Double, MetersPerSecond]] =
      5.0.withUnit[MetersPerSecond].pure[F]

    override def windDirection: F[Angle] = Angle.fromDoubleDegrees(60.0).pure[F]

    override def humidity: F[Double] = 20.0.pure[F]

    override def health: F[EpicsHealth] = EpicsHealth.Good.pure[F].widen
  }
}

object GwsKeywordsReaderEpics extends GwsDefaults {
  def apply[F[_]: Sync](sys: GwsEpics[F]): GwsKeywordReader[F] = new GwsKeywordReader[F] {

    override def temperature: F[Temperature[Double, Celsius]] =
      sys.ambientT

    override def dewPoint: F[DewPoint] =
      sys.dewPoint.map(DewPoint(_))

    override def airPressure: F[Quantity[Double, Bar]] =
      sys.airPressure

    override def windVelocity: F[Quantity[Double, MetersPerSecond]] =
      sys.windVelocity

    override def windDirection: F[Angle] =
      sys.windDirection

    override def humidity: F[Double] =
      sys.humidity

    override def health: F[EpicsHealth] =
      sys.health
  }
}
