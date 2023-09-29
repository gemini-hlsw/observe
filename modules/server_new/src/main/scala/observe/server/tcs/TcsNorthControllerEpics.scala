// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.data.*
import cats.effect.Async
import cats.syntax.all.*
import lucuma.core.enums.Site
import observe.model.enums.NodAndShuffleStage
import observe.server.ObserveFailure
import observe.server.altair.Altair
import observe.server.tcs.TcsController.*
import observe.server.tcs.TcsNorthController.*
import org.typelevel.log4cats.Logger

final case class TcsNorthControllerEpics[F[_]: Async: Logger](epicsSys: TcsEpics[F])
    extends TcsNorthController[F] {
  private val commonController = TcsControllerEpicsCommon[F, Site.GN.type](epicsSys)
  private val aoController     = TcsNorthControllerEpicsAo(epicsSys)

  override def applyConfig(
    subsystems: NonEmptySet[Subsystem],
    gaos:       Option[Altair[F]],
    tcs:        TcsNorthConfig
  ): F[Unit] =
    tcs match {
      case c: BasicTcsConfig[Site.GN.type] => commonController.applyBasicConfig(subsystems, c)
      case d: TcsNorthAoConfig             =>
        gaos
          .map(aoController.applyAoConfig(subsystems, _, d))
          .getOrElse(
            ObserveFailure.Execution("No Altair object defined for Altair step").raiseError[F, Unit]
          )
    }

  override def notifyObserveStart: F[Unit] = commonController.notifyObserveStart

  override def notifyObserveEnd: F[Unit] = commonController.notifyObserveEnd

  override def nod(
    subsystems: NonEmptySet[Subsystem],
    tcsConfig:  TcsNorthConfig
  )(stage: NodAndShuffleStage, offset: InstrumentOffset, guided: Boolean): F[Unit] =
    tcsConfig match {
      case c: BasicTcsConfig[Site.GN.type] => commonController.nod(subsystems, offset, guided, c)
      case _: TcsNorthAoConfig             =>
        ObserveFailure.Execution("N&S not supported when using Altair").raiseError[F, Unit]
    }
}
