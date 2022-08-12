// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gnirs

import cats.effect.Async
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import observe.model.dhs.ImageFileId
import observe.model.enum.ObserveCommandResult
import observe.server.InstrumentControllerSim
import observe.server.InstrumentSystem.ElapsedTime
import observe.server.Progress
import observe.server.gnirs.GnirsController.DCConfig
import observe.server.gnirs.GnirsController.GnirsConfig
import squants.Time
import squants.time.TimeConversions._

object GnirsControllerSim {
  def apply[F[_]: Logger: Async]: F[GnirsController[F]] =
    InstrumentControllerSim[F]("GNIRS").map { sim =>
      new GnirsController[F] {

        override def observe(fileId: ImageFileId, expTime: Time): F[ObserveCommandResult] =
          sim.observe(fileId, expTime)

        override def applyConfig(config: GnirsConfig): F[Unit] = sim.applyConfig(config)

        override def stopObserve: F[Unit] = sim.stopObserve

        override def abortObserve: F[Unit] = sim.abortObserve

        override def endObserve: F[Unit] = sim.endObserve

        override def observeProgress(total: Time): fs2.Stream[F, Progress] =
          sim.observeCountdown(total, ElapsedTime(0.seconds))

        override def calcTotalExposureTime(cfg: DCConfig): F[Time] =
          GnirsController.calcTotalExposureTime[F](cfg)
      }
    }
}
