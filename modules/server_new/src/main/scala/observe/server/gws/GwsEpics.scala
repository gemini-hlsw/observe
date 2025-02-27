// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gws

import cats.effect.IO
import cats.effect.Sync
import cats.syntax.all.*
import coulomb.*
import coulomb.Quantity
import coulomb.policy.standard.given
import coulomb.syntax.*
import coulomb.units.accepted.Millibar
import coulomb.units.temperature.*
import edu.gemini.epics.acm.CaService
import lucuma.core.math.Angle
import lucuma.core.math.units.MetersPerSecond
import observe.server.EpicsHealth
import observe.server.EpicsSystem
import observe.server.EpicsUtil.*
//import scala.language.implicitConversions

// unit definitions
//import coulomb.units.si.{*, given}

/**
 * GwsEpics wraps the non-functional parts of the EPICS ACM library to interact with the Weather
 * Server. It has all the objects used to read TCS status values and execute TCS commands.
 */
final class GwsEpics[F[_]: Sync] private (epicsService: CaService) {
  private val state = epicsService.getStatusAcceptor("gws::state")

  private def readD(name: String): F[Double] =
    safeAttributeSDoubleF[F](state.getDoubleAttribute(name))
  private def readI(name: String): F[Int]    =
    safeAttributeSIntF[F](state.getIntegerAttribute(name))

  def humidity: F[Double]                                = readD("humidity")
  def windVelocity: F[Quantity[Double, MetersPerSecond]] =
    readD("windspee").map(v => v.withUnit[MetersPerSecond])
  def airPressure: F[Quantity[Double, Bar]]              =
    readD("pressure").map(v => v.withUnit[Millibar].toUnit[Bar])
  def ambientT: F[Temperature[Double, Celsius]]          =
    readD("tambient").map(v => v.withTemperature[Celsius])
  def health: F[EpicsHealth]                             =
    readI("health").map[EpicsHealth](h => EpicsHealth.fromInt(h.intValue))
  def dewPoint: F[Temperature[Double, Celsius]]          =
    readD("dewpoint").map(v => v.withTemperature[Celsius])
  def windDirection: F[Angle]                            =
    readD("winddire").map(v => Angle.fromDoubleDegrees(v))

}

object GwsEpics extends EpicsSystem[GwsEpics[IO]] {
  override val className: String      = getClass.getName
  override val CA_CONFIG_FILE: String = "/Gws.xml"

  override def build[F[_]: Sync](service: CaService, tops: Map[String, String]): F[GwsEpics[IO]] =
    Sync[F].delay(new GwsEpics(service))
}
