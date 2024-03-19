// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gsaoi

import cats.Applicative
import cats.effect.Async
import cats.syntax.all.*
import lucuma.core.util.TimeSpan
import observe.model.dhs.ImageFileId
import observe.model.enums.ObserveCommandResult
import observe.server.InstrumentControllerSim
import observe.server.InstrumentSystem.ElapsedTime
import observe.server.Progress
import observe.server.gsaoi.GsaoiController.DCConfig
import observe.server.gsaoi.GsaoiController.GsaoiConfig
import org.typelevel.log4cats.Logger

object GsaoiControllerSim {
  def apply[F[_]: Logger: Async]: F[GsaoiFullHandler[F]] =
    InstrumentControllerSim[F]("GSAOI").map { sim =>
      new GsaoiFullHandler[F] {

        override def observe(fileId: ImageFileId, cfg: DCConfig): F[ObserveCommandResult] =
          sim.observe(fileId, calcTotalExposureTime(cfg))

        override def applyConfig(config: GsaoiConfig): F[Unit] =
          sim.applyConfig(config)

        override def stopObserve: F[Unit] = sim.stopObserve

        override def abortObserve: F[Unit] = sim.abortObserve

        override def endObserve: F[Unit] = sim.endObserve

        override def observeProgress(total: TimeSpan): fs2.Stream[F, Progress] =
          sim.observeCountdown(total, ElapsedTime(TimeSpan.Zero))

        override def currentState: F[GsaoiGuider.StepGuideState] = (new GsaoiGuider.StepGuideState {
          override def isGuideActive: Boolean = false

          override def isOdgwGuiding(odgwId: GsaoiGuider.OdgwId): Boolean = false
        }).pure[F]

        override def guide: F[Unit] = Applicative[F].unit

        override def endGuide: F[Unit] = Applicative[F].unit
      }
    }

}
