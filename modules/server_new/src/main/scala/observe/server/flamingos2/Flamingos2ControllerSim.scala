// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.flamingos2

import cats.MonadError
import cats.MonadThrow
import cats.effect.Async
import cats.effect.Ref
import cats.syntax.all.*
import fs2.Stream
import lucuma.core.util.TimeSpan
import observe.model.dhs.ImageFileId
import observe.model.enums.ObserveCommandResult
import observe.server.InstrumentControllerSim
import observe.server.InstrumentSystem.ElapsedTime
import observe.server.ObserveFailure.Execution
import observe.server.Progress
import observe.server.flamingos2.Flamingos2Controller.Flamingos2Config
import org.typelevel.log4cats.Logger

final case class Flamingos2ControllerSim[F[_]] private (sim: InstrumentControllerSim[F])
    extends Flamingos2Controller[F] {

  override def observe(fileId: ImageFileId, expTime: TimeSpan): F[ObserveCommandResult] =
    sim.observe(fileId, expTime)

  override def applyConfig(config: Flamingos2Config): F[Unit] =
    sim.applyConfig(config)

  override def endObserve: F[Unit] = sim.endObserve

  override def observeProgress(total: TimeSpan): Stream[F, Progress] =
    sim.observeCountdown(total, ElapsedTime(TimeSpan.Zero))
}

object Flamingos2ControllerSim {
  def apply[F[_]: Async: Logger]: F[Flamingos2Controller[F]] =
    InstrumentControllerSim("FLAMINGOS-2").map(Flamingos2ControllerSim(_))

}

/**
 * This controller will run correctly but fail at step `failAt`
 */
final case class Flamingos2ControllerSimBad[F[_]: MonadThrow: Logger] private (
  failAt:  Int,
  sim:     InstrumentControllerSim[F],
  counter: Ref[F, Int]
) extends Flamingos2Controller[F] {
  private val L = Logger[F]

  override def observe(fileId: ImageFileId, expTime: TimeSpan): F[ObserveCommandResult] =
    sim.observe(fileId, expTime)

  override def applyConfig(config: Flamingos2Config): F[Unit] =
    L.info(s"Simulate applying Flamingos-2 configuration $config") *>
      (counter.modify(x => (x + 1, x + 1)) >>= { c =>
        {
          counter.set(0) *>
            L.error(s"Error applying Flamingos-2 configuration") *>
            MonadError[F, Throwable].raiseError(Execution("simulated error"))
        }.whenA(c === failAt)
      }) <*
      L.info("Completed simulating Flamingos-2 configuration apply")

  override def endObserve: F[Unit] = sim.endObserve

  override def observeProgress(total: TimeSpan): Stream[F, Progress] =
    sim.observeCountdown(total, ElapsedTime(TimeSpan.Zero))
}

object Flamingos2ControllerSimBad {
  def apply[F[_]: Async: Logger](failAt: Int): F[Flamingos2Controller[F]] =
    (Ref.of[F, Int](0), InstrumentControllerSim("FLAMINGOS-2 (bad)")).mapN { (counter, sim) =>
      Flamingos2ControllerSimBad(
        failAt,
        sim,
        counter
      )
    }

}
