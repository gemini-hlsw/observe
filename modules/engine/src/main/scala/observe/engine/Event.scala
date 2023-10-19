// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.effect.Sync
import cats.syntax.all.*
import fs2.Stream
import lucuma.core.enums.Breakpoint
import lucuma.core.model.User
import observe.engine.SystemEvent.Null
import observe.model.ClientId
import observe.model.Observation
import observe.model.StepId

import java.time.Instant

import SystemEvent.*
import UserEvent.*

/**
 * Anything that can go through the Event Queue.
 */
sealed trait Event[F[_], S, U] extends Product with Serializable

object Event {
  final case class EventUser[F[_], S, U](ue: UserEvent[F, S, U]) extends Event[F, S, U]
  final case class EventSystem[F[_], S, U](se: SystemEvent)      extends Event[F, S, U]

  def start[F[_], S, U](id: Observation.Id, user: User, clientId: ClientId): Event[F, S, U] =
    EventUser[F, S, U](Start[F, S, U](id, user.some, clientId))
  def pause[F[_], S, U](id: Observation.Id, user: User): Event[F, S, U]                     =
    EventUser[F, S, U](Pause(id, user.some))
  def cancelPause[F[_], S, U](id: Observation.Id, user: User): Event[F, S, U]               =
    EventUser[F, S, U](CancelPause(id, user.some))
  def breakpoint[F[_], S, U](
    id:    Observation.Id,
    user:  User,
    steps: List[StepId],
    v:     Breakpoint
  ): Event[F, S, U] = EventUser[F, S, U](Breakpoints(id, user.some, steps, v))
  def skip[F[_], S, U](
    id:   Observation.Id,
    user: User,
    step: StepId,
    v:    Boolean
  ): Event[F, S, U] = EventUser[F, S, U](SkipMark(id, user.some, step, v))
  def poll[F[_], S, U](clientId: ClientId): Event[F, S, U]                                  =
    EventUser[F, S, U](Poll(clientId))
  def getState[F[_], S, U](f: S => Option[Stream[F, Event[F, S, U]]]): Event[F, S, U]       =
    EventUser[F, S, U](GetState(f))
  def modifyState[F[_], S, U](f: Handle[F, S, Event[F, S, U], U]): Event[F, S, U]           =
    EventUser[F, S, U](ModifyState(f))
  def actionStop[F[_], S, U](
    id: Observation.Id,
    f:  S => Option[Stream[F, Event[F, S, U]]]
  ): Event[F, S, U] = EventUser[F, S, U](ActionStop(id, f))
  def actionResume[F[_], S, U](
    id: Observation.Id,
    i:  Int,
    c:  Stream[F, Result]
  ): Event[F, S, U] =
    EventUser[F, S, U](ActionResume(id, i, c))
  def logDebugMsg[F[_], S, U](msg: String, ts: Instant): Event[F, S, U]                     =
    EventUser[F, S, U](LogDebug(msg, ts))
  def logDebugMsgF[F[_]: Sync, S, U](msg: String): F[Event[F, S, U]]                        =
    Sync[F].delay(Instant.now).map(t => EventUser[F, S, U](LogDebug(msg, t)))
  def logInfoMsg[F[_], S, U](msg: String, ts: Instant): Event[F, S, U]                      =
    EventUser[F, S, U](LogInfo(msg, ts))
  def logWarningMsg[F[_], S, U](msg: String, ts: Instant): Event[F, S, U]                   =
    EventUser[F, S, U](LogWarning(msg, ts))
  def logErrorMsg[F[_], S, U](msg: String, ts: Instant): Event[F, S, U]                     =
    EventUser[F, S, U](LogError(msg, ts))
  def logErrorMsgF[F[_]: Sync, S, U](msg: String): F[Event[F, S, U]]                        =
    Sync[F].delay(Instant.now).map(t => EventUser[F, S, U](LogError(msg, t)))

  def pure[F[_]: Sync, S, U](v: U): Event[F, S, U] = EventUser[F, S, U](Pure(v))

  def failed[F[_], S, U](id: Observation.Id, i: Int, e: Result.Error): Event[F, S, U]  =
    EventSystem[F, S, U](Failed(id, i, e))
  def completed[F[_], R <: Result.RetVal, S, U](
    id:     Observation.Id,
    stepId: StepId,
    i:      Int,
    r:      Result.OK[R]
  ): Event[F, S, U] = EventSystem[F, S, U](Completed(id, stepId, i, r))
  def stopCompleted[F[_], R <: Result.RetVal, S, U](
    id:     Observation.Id,
    stepId: StepId,
    i:      Int,
    r:      Result.OKStopped[R]
  ): Event[F, S, U] = EventSystem[F, S, U](StopCompleted(id, stepId, i, r))
  def aborted[F[_], R <: Result.RetVal, S, U](
    id:     Observation.Id,
    stepId: StepId,
    i:      Int,
    r:      Result.OKAborted[R]
  ): Event[F, S, U] = EventSystem[F, S, U](Aborted(id, stepId, i, r))
  def partial[F[_], R <: Result.PartialVal, S, U](
    id:     Observation.Id,
    stepId: StepId,
    i:      Int,
    r:      Result.Partial[R]
  ): Event[F, S, U] =
    EventSystem[F, S, U](PartialResult(id, stepId, i, r))
  def paused[F[_], S, U](id: Observation.Id, i: Int, c: Result.Paused): Event[F, S, U] =
    EventSystem[F, S, U](Paused(id, i, c))
  def breakpointReached[F[_], S, U](id: Observation.Id): Event[F, S, U]                =
    EventSystem[F, S, U](BreakpointReached(id))
  def busy[F[_], S, U](id: Observation.Id, clientId: ClientId): Event[F, S, U]         =
    EventSystem[F, S, U](Busy(id, clientId))
  def executed[F[_], S, U](id: Observation.Id): Event[F, S, U] = EventSystem[F, S, U](Executed(id))
  def executing[F[_], S, U](id: Observation.Id): Event[F, S, U] =
    EventSystem[F, S, U](Executing(id))
  def finished[F[_], S, U](id: Observation.Id): Event[F, S, U] = EventSystem[F, S, U](Finished(id))
  def nullEvent[F[_], S, U]: Event[F, S, U]                                         = EventSystem[F, S, U](Null)
  def singleRunCompleted[F[_], R <: Result.RetVal, S, U](
    c: ActionCoords,
    r: Result.OK[R]
  ): Event[F, S, U] =
    EventSystem[F, S, U](SingleRunCompleted(c, r))
  def singleRunFailed[F[_], S, U](c: ActionCoords, e: Result.Error): Event[F, S, U] =
    EventSystem[F, S, U](SingleRunFailed(c, e))

}
