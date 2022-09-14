// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.Eq
import cats.syntax.all.*
import cats.derived.*

enum StepState derives Eq:
  case Pending             extends StepState
  case Completed           extends StepState
  case Skipped             extends StepState
  case Aborted             extends StepState
  case Failed(msg: String) extends StepState
  case Running             extends StepState
  case Paused              extends StepState

  def canSetBreakpoint: Boolean = this match
    case StepState.Pending | StepState.Skipped | StepState.Paused | StepState.Running |
        StepState.Aborted =>
      true
    case _ => false

  def canSetSkipmark: Boolean = this match
    case StepState.Pending | StepState.Paused | StepState.Aborted => true
    case _ if hasError                                            => true
    case _                                                        => false

  def hasError: Boolean = this match
    case StepState.Failed(_) => true
    case _                   => false

  def isRunning: Boolean = this === StepState.Running

  def isPending: Boolean = this === StepState.Pending

  def runningOrComplete: Boolean = this match
    case StepState.Running | StepState.Completed => true
    case _                                       => false

  def isFinished: Boolean = this match
    case StepState.Completed | StepState.Skipped => true
    case _                                       => false

  def wasSkipped: Boolean = this === StepState.Skipped

  def canConfigure: Boolean = this match
    case StepState.Pending | StepState.Paused | StepState.Failed(_) | StepState.Aborted => true
    case _                                                                              => false
