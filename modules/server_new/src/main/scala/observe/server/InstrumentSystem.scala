// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.data.Kleisli
import fs2.Stream
import lucuma.core.enums.Instrument
import lucuma.core.util.TimeSpan
import observe.model.dhs.ImageFileId
import observe.model.enums.ObserveCommandResult
import observe.server.keywords.KeywordsClient

import java.time.temporal.ChronoUnit

trait InstrumentSystem[F[_]] extends System[F] {
  val resource: Instrument

  val contributorName: String

  def observeControl: InstrumentSystem.ObserveControl[F]

  def observe: Kleisli[F, ImageFileId, ObserveCommandResult]

  // Expected total observe lapse, used to calculate timeout
  def calcObserveTime: TimeSpan

  def observeTimeout: TimeSpan = TimeSpan.unsafeFromDuration(1, ChronoUnit.MINUTES)

  def keywordsClient: KeywordsClient[F]

  def observeProgress(
    total:   TimeSpan,
    elapsed: InstrumentSystem.ElapsedTime
  ): Stream[F, Progress]

  def instrumentActions: InstrumentActions[F]

}

object InstrumentSystem {
  val ObserveOperationsTimeout = TimeSpan.unsafeFromDuration(1, ChronoUnit.MINUTES)

  final case class StopObserveCmd[+F[_]](self: Boolean => F[Unit])
  final case class AbortObserveCmd[+F[_]](self: F[Unit])
  final case class PauseObserveCmd[+F[_]](self: Boolean => F[Unit])

  final case class ContinuePausedCmd[+F[_]](self: TimeSpan => F[ObserveCommandResult])
  final case class StopPausedCmd[+F[_]](self: F[ObserveCommandResult])
  final case class AbortPausedCmd[+F[_]](self: F[ObserveCommandResult])

  sealed trait ObserveControl[+F[_]] extends Product with Serializable
  case object Uncontrollable         extends ObserveControl[Nothing]
  final case class CompleteControl[+F[_]](
    stop:        StopObserveCmd[F],
    abort:       AbortObserveCmd[F],
    pause:       PauseObserveCmd[F],
    continue:    ContinuePausedCmd[F],
    stopPaused:  StopPausedCmd[F],
    abortPaused: AbortPausedCmd[F]
  ) extends ObserveControl[F]
  // Special class for instrument, that cannot pause/resume like IR instruments and GSAOI
  final case class UnpausableControl[+F[_]](stop: StopObserveCmd[F], abort: AbortObserveCmd[F])
      extends ObserveControl[F]

  final case class ElapsedTime(self: TimeSpan) extends AnyVal
}
