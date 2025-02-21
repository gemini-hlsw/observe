// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.data.StateT
import cats.effect.Temporal
import cats.syntax.all.*
import fs2.Stream
import lucuma.core.util.TimeSpan
import observe.model.ObserveStage

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.SECONDS

object ProgressUtil {
  private val PollPeriod = FiniteDuration(1, SECONDS)

  private def finiteDurationToTimeSpan(fd: FiniteDuration): TimeSpan =
    TimeSpan.unsafeFromMicroseconds(fd.toMicros)

  def fromF[F[_]: Temporal](f: TimeSpan => F[Progress]): Stream[F, Progress] =
    Stream
      .awakeEvery[F](PollPeriod)
      .evalMap(f.compose(finiteDurationToTimeSpan))

  def fromFOption[F[_]: Temporal](f: TimeSpan => F[Option[Progress]]): Stream[F, Progress] =
    Stream
      .awakeEvery[F](PollPeriod)
      .evalMap(f.compose(finiteDurationToTimeSpan))
      .collect { case Some(p) => p }

  def fromStateT[F[_]: Temporal, S](
    fs: FiniteDuration => StateT[F, S, Progress]
  ): S => Stream[F, Progress] = s0 =>
    Stream
      .awakeEvery[F](PollPeriod)
      .evalMapAccumulate(s0) { case (st, t) => fs(t).run(st) }
      .map(_._2)

  def fromStateTOption[F[_]: Temporal, S](
    fs: FiniteDuration => StateT[F, S, Option[Progress]]
  ): S => Stream[F, Progress] = s0 =>
    Stream
      .awakeEvery[F](PollPeriod)
      .evalMapAccumulate(s0) { case (st, t) => fs(t).run(st) }
      .map(_._2)
      .collect { case Some(p) => p }

  /**
   * Simple simulated countdown
   */
  def countdown[F[_]: Temporal](total: TimeSpan, elapsed: TimeSpan): Stream[F, Progress] =
    ProgressUtil
      .fromF[F] { (t: TimeSpan) =>
        val progress  = t +| elapsed
        val remaining = total -| progress
        val clipped   = if (remaining >= TimeSpan.Zero) remaining else TimeSpan.Zero
        ObsProgress(total, RemainingTime(clipped), ObserveStage.Exposure).pure[F].widen[Progress]
      }
      .takeThrough(_.remaining.self > TimeSpan.Zero)

  /**
   * Simulated countdown with simulated observation stage
   */
  def obsCountdown[F[_]: Temporal](total: TimeSpan, elapsed: TimeSpan): Stream[F, Progress] =
    Stream.emit(ObsProgress(total, RemainingTime(total), ObserveStage.Preparing)) ++
      countdown[F](total, elapsed) ++
      Stream.emit(ObsProgress(total, RemainingTime(TimeSpan.Zero), ObserveStage.ReadingOut))

  /**
   * Simulated countdown with observation stage provided by instrument
   */
  def obsCountdownWithObsStage[F[_]: Temporal](
    total:   TimeSpan,
    elapsed: TimeSpan,
    stage:   F[ObserveStage]
  ): Stream[F, Progress] =
    ProgressUtil
      .fromF[F] { (t: TimeSpan) =>
        val progress  = t +| elapsed
        val remaining = total -| progress
        val clipped   = if (remaining >= TimeSpan.Zero) remaining else TimeSpan.Zero
        stage
          .map(v => ObsProgress(total, RemainingTime(clipped), v))
          .widen[Progress]
      }
      .takeThrough(x => x.remaining.self > TimeSpan.Zero || x.stage === ObserveStage.Exposure)
}
