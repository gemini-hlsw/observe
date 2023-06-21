// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.data.NonEmptySet
import org.typelevel.log4cats.Logger
import observe.model.enums.NodAndShuffleStage
import observe.server.overrideLogMessage
import observe.server.gems.Gems
import observe.server.tcs.TcsSouthController.TcsSouthConfig

class TcsSouthControllerDisabled[F[_]: Logger] extends TcsSouthController[F] {
  override def applyConfig(
    subsystems: NonEmptySet[TcsController.Subsystem],
    gaos:       Option[Gems[F]],
    tc:         TcsSouthConfig
  ): F[Unit] =
    overrideLogMessage("TCS", "applyConfig")

  override def notifyObserveStart: F[Unit] = overrideLogMessage("TCS", "notifyObserveStart")

  override def notifyObserveEnd: F[Unit] = overrideLogMessage("TCS", "notifyObserveEnd")

  override def nod(
    subsystems: NonEmptySet[TcsController.Subsystem],
    tcsConfig:  TcsSouthConfig
  )(stage:      NodAndShuffleStage, offset: TcsController.InstrumentOffset, guided: Boolean): F[Unit] =
    overrideLogMessage("TCS", s"nod(${stage.symbol})")
}
