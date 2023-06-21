// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.data.NonEmptySet
import cats.implicits.*
import org.typelevel.log4cats.Logger
import observe.model.enums.NodAndShuffleStage
import observe.server.tcs.TcsController.*

class TcsControllerSim[F[_]: Logger] {

  def info(msg: String): F[Unit] = Logger[F].info(msg)

  def applyConfig(subsystems: NonEmptySet[Subsystem]): F[Unit] =
    info(s"Simulate TCS configuration for ${subsystems.toList.mkString("(", ", ", ")")}")

  def notifyObserveStart: F[Unit] = info("Simulate TCS observe")

  def notifyObserveEnd: F[Unit] = info("Simulate TCS endObserve")

  def nod(stage: NodAndShuffleStage, offset: InstrumentOffset, guided: Boolean): F[Unit] =
    info(s"Simulate TCS Nod to position ${stage.symbol}, offset=$offset, guided=$guided")
}
