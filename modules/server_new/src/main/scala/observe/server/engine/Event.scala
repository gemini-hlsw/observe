// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.engine

import cats.effect.Sync
import cats.syntax.all.*
import fs2.Stream
import lucuma.core.enums.Breakpoint
import lucuma.core.model.User
import lucuma.core.model.sequence.Step
import observe.model.ClientId
import observe.model.Observation
import observe.server.EngineState
import observe.server.SeqEvent

import java.time.Instant

import SystemEvent.*
import UserEvent.*

/**
 * Anything that can go through the Event Queue.
 */
sealed trait Event[F[_]] extends Product with Serializable

object Event {
  case class EventUser[F[_]](ue: UserEvent[F])  extends Event[F]
  case class EventSystem[F[_]](se: SystemEvent) extends Event[F]

  def start[F[_]](obsId: Observation.Id, user: User, clientId: ClientId): Event[F] =
    EventUser[F](Start[F](obsId, user.some, clientId))
  def pause[F[_]](obsId: Observation.Id, user: User): Event[F]                     =
    EventUser[F](Pause(obsId, user.some))
  def cancelPause[F[_]](obsId: Observation.Id, user: User): Event[F]               =
    EventUser[F](CancelPause(obsId, user.some))
  def breakpoints[F[_]](
    id:    Observation.Id,
    user:  User,
    steps: Set[Step.Id],
    v:     Breakpoint
  ): Event[F] = EventUser[F](Breakpoints(id, user.some, steps, v))
  def poll[F[_]](clientId: ClientId): Event[F]                                     =
    EventUser[F](Poll(clientId))
  def getState[F[_]](f: EngineState[F] => Stream[F, Event[F]]): Event[F]           =
    EventUser[F](GetState(f))
  def modifyState[F[_]](f: EngineHandle[F, SeqEvent]): Event[F]                    =
    EventUser[F](ModifyState(f))
  def actionStop[F[_]](
    obsId: Observation.Id,
    f:     EngineState[F] => Stream[F, Event[F]]
  ): Event[F] = EventUser[F](ActionStop(obsId, f))
  def actionResume[F[_]](
    obsId: Observation.Id,
    i:     Int,
    c:     Stream[F, Result]
  ): Event[F] =
    EventUser[F](ActionResume(obsId, i, c))
  def logDebugMsg[F[_]](msg: String, ts: Instant): Event[F]                        =
    EventUser[F](LogDebug(msg, ts))
  def logDebugMsgF[F[_]: Sync](msg: String): F[Event[F]]                           =
    Sync[F].delay(Instant.now).map(t => EventUser[F](LogDebug(msg, t)))
  def logInfoMsg[F[_]](msg: String, ts: Instant): Event[F]                         =
    EventUser[F](LogInfo(msg, ts))
  def logWarningMsg[F[_]](msg: String, ts: Instant): Event[F]                      =
    EventUser[F](LogWarning(msg, ts))
  def logErrorMsg[F[_]](msg: String, ts: Instant): Event[F]                        =
    EventUser[F](LogError(msg, ts))
  def logErrorMsgF[F[_]: Sync](msg: String): F[Event[F]]                           =
    Sync[F].delay(Instant.now).map(t => EventUser[F](LogError(msg, t)))

  def pure[F[_]](v: SeqEvent): Event[F] = EventUser[F](Pure(v))

  def failed[F[_]](obsId: Observation.Id, i: Int, e: Result.Error): Event[F]  =
    EventSystem[F](Failed(obsId, i, e))
  def completed[F[_], R <: Result.RetVal](
    id:     Observation.Id,
    stepId: Step.Id,
    i:      Int,
    r:      Result.OK[R]
  ): Event[F] = EventSystem[F](Completed(id, stepId, i, r))
  def stopCompleted[F[_], R <: Result.RetVal](
    id:     Observation.Id,
    stepId: Step.Id,
    i:      Int,
    r:      Result.OKStopped[R]
  ): Event[F] = EventSystem[F](StopCompleted(id, stepId, i, r))
  def aborted[F[_], R <: Result.RetVal](
    id:     Observation.Id,
    stepId: Step.Id,
    i:      Int,
    r:      Result.OKAborted[R]
  ): Event[F] = EventSystem[F](Aborted(id, stepId, i, r))
  def partial[F[_], R <: Result.PartialVal](
    id:     Observation.Id,
    stepId: Step.Id,
    i:      Int,
    r:      Result.Partial[R]
  ): Event[F] =
    EventSystem[F](PartialResult(id, stepId, i, r))
  def paused[F[_]](obsId: Observation.Id, i: Int, c: Result.Paused): Event[F] =
    EventSystem[F](Paused(obsId, i, c))
  def breakpointReached[F[_]](obsId: Observation.Id): Event[F]                =
    EventSystem[F](BreakpointReached(obsId))
  def sequencePaused[F[_]](obsId: Observation.Id): Event[F]                   =
    EventSystem[F](SequencePaused(obsId))
  def busy[F[_]](obsId: Observation.Id, clientId: ClientId): Event[F]         =
    EventSystem[F](Busy(obsId, clientId))
  def executed[F[_]](obsId: Observation.Id): Event[F]                         =
    EventSystem[F](Executed(obsId))
  def executing[F[_]](obsId: Observation.Id): Event[F]                        =
    EventSystem[F](Executing(obsId))
  def stepComplete[F[_]](obsId: Observation.Id): Event[F]                     =
    EventSystem[F](StepComplete(obsId))
  def finished[F[_]](obsId: Observation.Id): Event[F]                         =
    EventSystem[F](SequenceComplete(obsId))
  def nullEvent[F[_]]: Event[F]                                               = EventSystem[F](Null)
  def singleRunCompleted[F[_], R <: Result.RetVal](
    c: ActionCoords,
    r: Result.OK[R]
  ): Event[F] =
    EventSystem[F](SingleRunCompleted(c, r))
  def singleRunFailed[F[_]](c: ActionCoords, e: Result.Error): Event[F]       =
    EventSystem[F](SingleRunFailed(c, e))

}
