// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.effect.IO
import cats.effect.Ref
import lucuma.core.util.TimeSpan
import munit.CatsEffectSuite
import observe.model.dhs.*
import observe.model.enums.ObserveCommandResult
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import java.time.temporal.ChronoUnit
import scala.concurrent.duration.*

class InstrumentControllerSimSuite extends CatsEffectSuite {
  private given Logger[IO] = NoOpLogger.impl[IO]

  val tick = FiniteDuration(250, MILLISECONDS)

  def simulator: InstrumentControllerSim[IO] =
    InstrumentControllerSim.unsafeWithTimes[IO](
      "sim",
      TimeSpan.unsafeFromDuration(10, ChronoUnit.MILLIS),
      TimeSpan.unsafeFromDuration(5, ChronoUnit.MILLIS),
      TimeSpan.unsafeFromDuration(1, ChronoUnit.SECONDS)
    )

  test("simulation doesn't stack overflow".ignore) {
    val obsStateRef = Ref.unsafe[IO, InstrumentControllerSim.ObserveState](
      InstrumentControllerSim.ObserveState(false, false, false, TimeSpan.Max)
    )
    val sim         = new InstrumentControllerSim.InstrumentControllerSimImpl[IO](
      "sim",
      false,
      TimeSpan.unsafeFromDuration(10, ChronoUnit.MILLIS),
      TimeSpan.unsafeFromDuration(5, ChronoUnit.MILLIS),
      TimeSpan.unsafeFromDuration(1, ChronoUnit.SECONDS),
      obsStateRef
    )
    // We make a very long observation here to ensure we don't stack overflow
    sim.observeTic(None).map(assertEquals(_, ObserveCommandResult.Success))
  }

  test("normal observation") {
    simulator
      .observe(toImageFileId("S001"), TimeSpan.unsafeFromDuration(5, ChronoUnit.SECONDS))
      .map(assertEquals(_, ObserveCommandResult.Success))
  }

  test("pause observation") {
    val sim = simulator
    for {
      f <-
        sim.observe(toImageFileId("S001"), TimeSpan.unsafeFromDuration(2, ChronoUnit.SECONDS)).start
      _ <- IO.sleep(tick) // give it enough time for at least one tick
      _ <- sim.pauseObserve
      r <- f.joinWithNever
    } yield assertEquals(r, ObserveCommandResult.Paused)
  }

  test("abort observation") {
    val sim = simulator
    for {
      f <-
        sim.observe(toImageFileId("S001"), TimeSpan.unsafeFromDuration(2, ChronoUnit.SECONDS)).start
      _ <- IO.sleep(tick) // give it enough time for at least one tick
      _ <- sim.abortObserve
      r <- f.joinWithNever
    } yield assertEquals(r, ObserveCommandResult.Aborted)
  }

  test("stop observation") {
    val sim = simulator
    for {
      f <-
        sim.observe(toImageFileId("S001"), TimeSpan.unsafeFromDuration(2, ChronoUnit.SECONDS)).start
      _ <- IO.sleep(tick) // give it enough time for at least one tick
      _ <- sim.stopObserve
      r <- f.joinWithNever
    } yield assertEquals(r, ObserveCommandResult.Stopped)
  }

  test("pause/stop pause observation") {
    val sim = simulator
    for {
      f <- sim
             .observe(toImageFileId("S001"), TimeSpan.unsafeFromDuration(900, ChronoUnit.MILLIS))
             .start
      _ <- IO.sleep(tick) // give it enough time for at least one tick
      _ <- sim.pauseObserve
      _ <- IO.sleep(tick) // give it enough time for at least one tick
      r <- sim.stopPaused
      _ <- f.joinWithNever
    } yield assertEquals(r, ObserveCommandResult.Stopped)
  }

  test("pause/resume observation") {
    val sim = simulator
    for {
      f <- sim
             .observe(toImageFileId("S001"), TimeSpan.unsafeFromDuration(900, ChronoUnit.MILLIS))
             .start
      _ <- IO.sleep(tick) // give it enough time for at least one tick
      _ <- sim.pauseObserve
      r <- sim.resumePaused
      _ <- f.joinWithNever
    } yield assertEquals(r, ObserveCommandResult.Success)
  }

  test("pause/stop observation") {
    val sim = simulator
    for {
      f <-
        sim.observe(toImageFileId("S001"), TimeSpan.unsafeFromDuration(2, ChronoUnit.SECONDS)).start
      _ <- IO.sleep(tick) // give it enough time for at least one tick
      _ <- sim.pauseObserve
      _ <- IO.sleep(tick) // give it enough time for at least one tick
      _ <- sim.stopObserve
      _ <- IO.sleep(tick) // give it enough time for at least one tick
      r <- f.joinWithNever
    } yield assertEquals(r, ObserveCommandResult.Paused)

    // FIXME The simulator should return Stopped
    // assertEquals(r, ObserveCommandResult.Stopped)
  }

  test("pause/abort paused observation") {
    val sim = simulator
    for {
      f <- sim
             .observe(toImageFileId("S001"), TimeSpan.unsafeFromDuration(900, ChronoUnit.MILLIS))
             .start
      _ <- IO.sleep(tick) // give it enough time for at least one tick
      _ <- sim.pauseObserve
      _ <- IO.sleep(tick) // give it enough time for at least one tick
      r <- sim.abortPaused
      _ <- f.joinWithNever
    } yield assertEquals(r, ObserveCommandResult.Aborted)
  }

  test("pause/abort observation") {
    val sim = simulator
    for {
      f <-
        sim.observe(toImageFileId("S001"), TimeSpan.unsafeFromDuration(2, ChronoUnit.SECONDS)).start
      _ <- IO.sleep(tick) // give it enough time for at least one tick
      _ <- sim.pauseObserve
      _ <- IO.sleep(tick) // give it enough time for at least one tick
      _ <- sim.abortObserve
      r <- f.joinWithNever
    } yield assertEquals(r, ObserveCommandResult.Paused)

    // FIXME The simulator should return Stopped
    // assertEquals(r, ObserveCommandResult.Aborted)

  }
}
