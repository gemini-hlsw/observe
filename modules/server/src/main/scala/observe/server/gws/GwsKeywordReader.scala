// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gws

import cats.Applicative
import cats.effect.Sync
import cats.syntax.all.*
import observe.server.EpicsHealth
import observe.server.keywords.*
import shapeless.tag
import shapeless.tag.@@
import squants.Angle
import squants.Temperature
import squants.Velocity
import squants.motion.MetersPerSecond
import squants.motion.Pressure
import squants.motion.StandardAtmospheres
import squants.space.Degrees
import squants.thermal.Celsius

trait DewPoint

trait GwsKeywordReader[F[_]] {
  def health: F[EpicsHealth]

  def temperature: F[Temperature]

  def dewPoint: F[Temperature @@ DewPoint]

  def airPressure: F[Pressure]

  def windVelocity: F[Velocity]

  def windDirection: F[Angle]

  def humidity: F[Double]
}

trait GwsDefaults {
  def toDewPoint(t: Temperature): Temperature @@ DewPoint =
    tag[DewPoint][Temperature](t)

  // Default value for quantities
  given DefaultHeaderValue[Temperature] =
    DefaultHeaderValue[Double].map(Celsius(_))

  given DefaultHeaderValue[Temperature @@ DewPoint] =
    DefaultHeaderValue[Temperature].map(toDewPoint)

  given DefaultHeaderValue[Pressure] =
    DefaultHeaderValue[Double].map(StandardAtmospheres(_))

  given DefaultHeaderValue[Velocity] =
    DefaultHeaderValue[Double].map(MetersPerSecond(_))

  given DefaultHeaderValue[Angle] =
    DefaultHeaderValue[Double].map(Degrees(_))

}

object DummyGwsKeywordsReader extends GwsDefaults {
  def apply[F[_]: Applicative]: GwsKeywordReader[F] = new GwsKeywordReader[F] {
    override def temperature: F[Temperature] = Celsius(15.0).pure[F]

    override def dewPoint: F[Temperature @@ DewPoint] =
      toDewPoint(Celsius(1.0)).pure[F]

    override def airPressure: F[Pressure] = StandardAtmospheres(1.0).pure[F]

    override def windVelocity: F[Velocity] = MetersPerSecond(5).pure[F]

    override def windDirection: F[Angle] = Degrees(60.0).pure[F]

    override def humidity: F[Double] = 20.0.pure[F]

    override def health: F[EpicsHealth] = EpicsHealth.Good.pure[F].widen
  }
}

object GwsKeywordsReaderEpics extends GwsDefaults {
  def apply[F[_]: Sync](sys: GwsEpics[F]): GwsKeywordReader[F] = new GwsKeywordReader[F] {

    override def temperature: F[Temperature] =
      sys.ambientT

    override def dewPoint: F[Temperature @@ DewPoint] =
      sys.dewPoint.map(tag[DewPoint][Temperature](_))

    override def airPressure: F[Pressure] =
      sys.airPressure

    override def windVelocity: F[Velocity] =
      sys.windVelocity

    override def windDirection: F[Angle] =
      sys.windDirection

    override def humidity: F[Double] =
      sys.humidity

    override def health: F[EpicsHealth] =
      sys.health
  }
}
