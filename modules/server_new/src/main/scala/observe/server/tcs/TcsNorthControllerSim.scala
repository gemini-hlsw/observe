// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.Applicative
import cats.data.NonEmptySet
import cats.implicits.*
import observe.model.enums.NodAndShuffleStage
import observe.server.altair.Altair
import observe.server.tcs.TcsController.InstrumentOffset
import observe.server.tcs.TcsController.Subsystem
import observe.server.tcs.TcsNorthController.TcsNorthConfig
import observe.server.tcs.TcsNorthController.given
import org.typelevel.log4cats.Logger

class TcsNorthControllerSim[F[_]: Applicative: Logger] private extends TcsNorthController[F] {
  val sim = new TcsControllerSim[F]
  val L   = Logger[F]

  override def applyConfig(
    subsystems: NonEmptySet[Subsystem],
    gaos:       Option[Altair[F]],
    tc:         TcsNorthConfig
  ): F[Unit] =
    L.debug("Start TCS configuration") *>
      L.debug(s"TCS configuration: ${tc.show}") *>
      sim.applyConfig(subsystems) *>
      L.debug("Completed TCS configuration")

  override def notifyObserveStart: F[Unit] = sim.notifyObserveStart

  override def notifyObserveEnd: F[Unit] = sim.notifyObserveEnd

  override def nod(
    subsystems: NonEmptySet[Subsystem],
    tcsConfig:  TcsNorthConfig
  )(stage: NodAndShuffleStage, offset: InstrumentOffset, guided: Boolean): F[Unit] =
    sim.nod(stage, offset, guided)

}

object TcsNorthControllerSim {

  def apply[F[_]: Applicative: Logger]: TcsNorthController[F] = new TcsNorthControllerSim[F]

}
