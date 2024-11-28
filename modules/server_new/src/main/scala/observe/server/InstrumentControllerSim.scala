// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.*
import cats.effect.Ref
import cats.effect.Sync
import cats.effect.Temporal
import cats.effect.kernel.Async
import cats.syntax.all.*
import fs2.Stream
import gov.aps.jca.TimeoutException
import lucuma.core.util.TimeSpan
import monocle.Focus
import monocle.Lens
import monocle.syntax.all.*
import mouse.all.*
import observe.model.dhs.ImageFileId
import observe.model.enums.ObserveCommandResult
import observe.server.InstrumentSystem.ElapsedTime
import observe.server.ObserveFailure.ObserveException
import org.typelevel.log4cats.Logger

import java.time.temporal.ChronoUnit
import scala.concurrent.duration.*

sealed trait InstrumentControllerSim[F[_]] {
  def log(msg: => String): F[Unit]

  def observe(fileId: ImageFileId, expTime: TimeSpan): F[ObserveCommandResult]

  def applyConfig[C: Show](config: C): F[Unit]

  def stopObserve: F[Unit]

  def abortObserve: F[Unit]

  def endObserve: F[Unit]

  def pauseObserve: F[Unit]

  def resumePaused: F[ObserveCommandResult]

  def stopPaused: F[ObserveCommandResult]

  def abortPaused: F[ObserveCommandResult]

  def observeCountdown(total: TimeSpan, elapsed: ElapsedTime): Stream[F, Progress]

}

object InstrumentControllerSim {
  final case class ObserveState(
    stopFlag:      Boolean,
    abortFlag:     Boolean,
    pauseFlag:     Boolean,
    remainingTime: TimeSpan
  )

  object ObserveState {
    val Zero: ObserveState =
      ObserveState(
        stopFlag = false,
        abortFlag = false,
        pauseFlag = false,
        remainingTime = TimeSpan.Zero
      )

    val pauseFalse = (o: ObserveState) =>
      (o.focus(_.pauseFlag).replace(false), o.focus(_.pauseFlag).replace(false))

    def unsafeRef[F[_]: Sync]: Ref[F, ObserveState] = Ref.unsafe(Zero)

    def ref[F[_]: Sync]: F[Ref[F, ObserveState]] = Ref.of(Zero)
  }

  private[server] final class InstrumentControllerSimImpl[F[_]](
    name:               String,
    useTimeout:         Boolean,
    readOutDelay:       TimeSpan,
    stopObserveDelay:   TimeSpan,
    configurationDelay: TimeSpan,
    obsStateRef:        Ref[F, ObserveState]
  )(using val F: MonadThrow[F], L: Logger[F], T: Temporal[F])
      extends InstrumentControllerSim[F] {
    println(readOutDelay)
    private val TIC = TimeSpan.unsafeFromMicroseconds(200L)

    def log(msg: => String): F[Unit] =
      L.info(msg)

    private def tupledUpdate[A, B](f: A => B) = (x: A) => (f(x), f(x))

    private[server] def observeTic(
      timeout: Option[TimeSpan]
    ): F[ObserveCommandResult] = obsStateRef.get.flatMap { observeState =>
      if (observeState.stopFlag) {
        ObserveCommandResult.Stopped.pure[F].widen
      } else if (observeState.abortFlag) {
        ObserveCommandResult.Aborted.pure[F].widen
      } else if (observeState.pauseFlag) {
        obsStateRef.update(
          _.focus(_.remainingTime).replace(observeState.remainingTime)
        ) *>
          ObserveCommandResult.Paused.pure[F].widen
      } else if (timeout.exists(_ <= TimeSpan.Zero)) {
        F.raiseError(ObserveException(new TimeoutException()))
      } else if (observeState.remainingTime < TIC) {
        log(s"Simulate $name observation completed") *>
          ObserveCommandResult.Success.pure[F].widen
      } else {
        val upd: ObserveState => ObserveState = _.focus(_.remainingTime).modify(_ -| TIC)
        // Use flatMap to ensure we don't stack overflow
        obsStateRef.modify(tupledUpdate(upd)) *>
          T.sleep(FiniteDuration(TIC.toMicroseconds, MICROSECONDS)) *>
          observeTic(timeout.map(_ -| TIC))
      }
    }

    def observe(fileId: ImageFileId, expTime: TimeSpan): F[ObserveCommandResult] = {
      val totalTime = TimeSpan.fromSeconds(10).get // expTime +| readOutDelay
      log(s"Simulate taking $name observation with label $fileId") *> {
        val upd = { (s: ObserveState) => s.focus(_.stopFlag).replace(false) } >>> {
          _.focus(_.pauseFlag).replace(false)
        } >>> { _.focus(_.abortFlag).replace(false) } >>> {
          _.focus(_.remainingTime).replace(totalTime)
        }
        obsStateRef.modify(tupledUpdate(upd))
      } *> observeTic(useTimeout.option(totalTime +| (TIC *| 2)))
    }

    def applyConfig[C: Show](config: C): F[Unit] =
      log(s"Simulate applying $name configuration ${config.show}") *>
        T.sleep(FiniteDuration(configurationDelay.toMicroseconds, MICROSECONDS))

    def stopObserve: F[Unit] =
      log(s"Simulate stopping $name exposure") *>
        T.sleep(FiniteDuration(stopObserveDelay.toMicroseconds, MICROSECONDS)) *>
        obsStateRef.update(Focus[ObserveState](_.stopFlag).replace(true))

    def abortObserve: F[Unit] =
      log(s"Simulate aborting $name exposure") *>
        obsStateRef.update(Focus[ObserveState](_.abortFlag).replace(true))

    def endObserve: F[Unit] =
      log(s"Simulate sending endObserve to $name")

    def pauseObserve: F[Unit] =
      log(s"Simulate pausing $name exposure") *>
        obsStateRef.update(Focus[ObserveState](_.pauseFlag).replace(true))

    def resumePaused: F[ObserveCommandResult] =
      log(s"Simulate resuming $name observation") *> {
        val upd = { (s: ObserveState) => s.focus(_.stopFlag).replace(false) } >>> {
          _.focus(_.pauseFlag).replace(false)
        } >>> { _.focus(_.abortFlag).replace(false) }
        obsStateRef.modify(tupledUpdate(upd))
      } >>= { s => observeTic(useTimeout.option(s.remainingTime +| (TIC *| 2))) }

    def stopPaused: F[ObserveCommandResult] =
      log(s"Simulate stopping $name paused observation") *> {
        val upd = { (s: ObserveState) => s.focus(_.stopFlag).replace(true) } >>> {
          _.focus(_.pauseFlag).replace(false)
        } >>> { _.focus(_.abortFlag).replace(false) } >>> {
          _.focus(_.remainingTime).replace(TimeSpan.unsafeFromMicroseconds(1000))
        }
        obsStateRef.modify(tupledUpdate(upd))
      } *> observeTic(none)

    def abortPaused: F[ObserveCommandResult] =
      log(s"Simulate aborting $name paused observation") *> {
        val upd = { (s: ObserveState) => s.focus(_.stopFlag).replace(false) } >>> {
          _.focus(_.pauseFlag).replace(false)
        } >>> { _.focus(_.abortFlag).replace(true) } >>> {
          _.focus(_.remainingTime).replace(TimeSpan.unsafeFromMicroseconds(1000))
        }
        obsStateRef.modify(tupledUpdate(upd))
      } *> observeTic(none)

    def observeCountdown(
      total:   TimeSpan,
      elapsed: ElapsedTime
    ): Stream[F, Progress] =
      ProgressUtil.obsCountdown[F](total, elapsed.self)

  }

  def apply[F[_]: Logger: Async](name: String): F[InstrumentControllerSim[F]] =
    InstrumentControllerSim.ObserveState.ref[F].map { obsStateRef =>
      new InstrumentControllerSimImpl[F](
        name,
        false,
        TimeSpan.unsafeFromDuration(5, ChronoUnit.SECONDS),
        TimeSpan.unsafeFromDuration(1500, ChronoUnit.MILLIS),
        TimeSpan.unsafeFromDuration(5, ChronoUnit.SECONDS),
        obsStateRef
      )
    }

  def unsafeWithTimes[F[_]: Async: Logger](
    name:               String,
    readOutDelay:       TimeSpan,
    stopObserveDelay:   TimeSpan,
    configurationDelay: TimeSpan
  ): InstrumentControllerSim[F] = {
    val obsStateRef = InstrumentControllerSim.ObserveState.unsafeRef[F]
    new InstrumentControllerSimImpl[F](
      name,
      true,
      readOutDelay,
      stopObserveDelay,
      configurationDelay,
      obsStateRef
    )
  }

}
