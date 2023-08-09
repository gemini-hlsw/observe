// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.data.StateT
import cats.effect.Temporal
import cats.syntax.all.*
import fs2.Stream
import observe.model.ObserveStage

import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS}

object ProgressUtil {
  private val PollPeriod = FiniteDuration(1, SECONDS)

  def fromF[F[_]: Temporal](f: FiniteDuration => F[Progress]): Stream[F, Progress] =
    Stream.awakeEvery[F](PollPeriod).evalMap(f)

  def fromFOption[F[_]: Temporal](
    f: FiniteDuration => F[Option[Progress]]
  ): Stream[F, Progress] =
    Stream.awakeEvery[F](PollPeriod).evalMap(f).collect { case Some(p) => p }

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
  def countdown[F[_]: Temporal](
    total:   FiniteDuration,
    elapsed: FiniteDuration
  ): Stream[F, Progress] =
    ProgressUtil
      .fromF[F] { (t: FiniteDuration) =>
        val progress  = t + elapsed
        val remaining = total - progress
        val clipped   = if (remaining.toMillis >= 0) remaining else Duration.Zero
        ObsProgress(total, RemainingTime(clipped), ObserveStage.Acquiring).pure[F].widen[Progress]
      }
      .takeThrough(_.remaining.self > Duration.Zero)

  /**
   * Simulated countdown with simulated observation stage
   */
  def obsCountdown[F[_]: Temporal](
    total:   FiniteDuration,
    elapsed: FiniteDuration
  ): Stream[F, Progress] =
    Stream.emit(ObsProgress(total, RemainingTime(total), ObserveStage.Preparing)) ++
      countdown[F](total, elapsed) ++
      Stream.emit(ObsProgress(total, RemainingTime(Duration.Zero), ObserveStage.ReadingOut))

  /**
   * Simulated countdown with observation stage provided by instrument
   */
  def obsCountdownWithObsStage[F[_]: Temporal](
    total:   FiniteDuration,
    elapsed: FiniteDuration,
    stage:   F[ObserveStage]
  ): Stream[F, Progress] =
    ProgressUtil
      .fromF[F] { (t: FiniteDuration) =>
        val progress  = t + elapsed
        val remaining = total - progress
        val clipped   = if (remaining >= Duration.Zero) remaining else Duration.Zero
        stage.map(v => ObsProgress(total, RemainingTime(clipped), v)).widen[Progress]
      }
      .takeThrough(x => x.remaining.self > Duration.Zero || x.stage === ObserveStage.Acquiring)
}
