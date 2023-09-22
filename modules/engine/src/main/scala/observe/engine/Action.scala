// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import fs2.Stream
import monocle.Focus
import monocle.Lens
import observe.model.ActionType
import observe.model.enums.ActionStatus

import Result.{Error, PartialVal, PauseContext, RetVal}

case class Action[F[_]](
  kind:  ActionType,
  gen:   Stream[F, Result],
  state: Action.State[F]
)

object Action {

  def state[F[_]]: Lens[Action[F], Action.State[F]] =
    Focus[Action[F]](_.state)

  def runStateL[F[_]]: Lens[Action[F], ActionState] =
    state.andThen(Focus[State[F]](_.runState))

  case class State[F[_]](
    runState: ActionState,
    partials: List[PartialVal]
  )

  sealed trait ActionState {
    def isIdle: Boolean = false

    def errored: Boolean = this match {
      case ActionState.Failed(_) => true
      case _                     => false
    }

    def finished: Boolean = this match {
      case ActionState.Failed(_)    => true
      case ActionState.Completed(_) => true
      case ActionState.Aborted      => true
      case _                        => false
    }

    def completed: Boolean = this match {
      case ActionState.Completed(_) => true
      case _                        => false
    }

    def paused: Boolean = this match {
      case ActionState.Paused(_) => true
      case _                     => false
    }

    def active: Boolean = this match {
      case ActionState.Paused(_) | ActionState.Started => true
      case _                                           => false
    }

    def started: Boolean = this match {
      case ActionState.Started => true
      case _                   => false
    }

    def aborted: Boolean = this match {
      case ActionState.Aborted => true
      case _                   => false
    }

    def actionStatus: ActionStatus = ActionState.actionStateToStatus(this)

  }

  object ActionState {

    case object Idle                        extends ActionState {
      override val isIdle: Boolean = true
    }
    case object Started                     extends ActionState
    case class Paused(ctx: PauseContext)    extends ActionState
    case class Completed[V <: RetVal](r: V) extends ActionState
    case class Failed(e: Error)             extends ActionState
    case object Aborted                     extends ActionState {
      override val isIdle: Boolean = true
    }
    private def actionStateToStatus[F[_]](s: ActionState): ActionStatus =
      s match {
        case Idle         => ActionStatus.Pending
        case Completed(_) => ActionStatus.Completed
        case Started      => ActionStatus.Running
        case Failed(_)    => ActionStatus.Failed
        case _: Paused    => ActionStatus.Paused
        case Aborted      => ActionStatus.Aborted
      }

  }

  def errored[F[_]](ar: Action[F]): Boolean = ar.state.runState.errored

  def finished[F[_]](ar: Action[F]): Boolean = ar.state.runState.finished

  def completed[F[_]](ar: Action[F]): Boolean = ar.state.runState.completed

  def paused[F[_]](ar: Action[F]): Boolean = ar.state.runState.paused

  def active[F[_]](ar: Action[F]): Boolean = ar.state.runState.active

  def aborted[F[_]](ar: Action[F]): Boolean = ar.state.runState.aborted
}
