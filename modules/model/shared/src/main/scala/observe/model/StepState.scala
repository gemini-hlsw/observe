// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import io.circe.*

enum StepState derives Eq, Decoder, Encoder.AsObject:
  case Pending             extends StepState
  case Completed           extends StepState
  case Skipped             extends StepState
  case Aborted             extends StepState
  case Failed(msg: String) extends StepState
  case Running             extends StepState
  case Paused              extends StepState

  lazy val canSetBreakpoint: Boolean = this match
    case StepState.Pending | StepState.Skipped | StepState.Paused | StepState.Running |
        StepState.Aborted =>
      true
    case _ => false

  lazy val canSetSkipmark: Boolean = this match
    case StepState.Pending | StepState.Paused | StepState.Aborted => true
    case _ if hasError                                            => true
    case _                                                        => false

  lazy val hasError: Boolean = this match
    case StepState.Failed(_) => true
    case _                   => false

  lazy val isRunning: Boolean = this === StepState.Running

  lazy val isPending: Boolean = this === StepState.Pending

  lazy val runningOrComplete: Boolean = this match
    case StepState.Running | StepState.Completed => true
    case _                                       => false

  lazy val isFinished: Boolean = this match
    case StepState.Completed | StepState.Skipped => true
    case _                                       => false

  lazy val wasSkipped: Boolean = this === StepState.Skipped

  lazy val canConfigure: Boolean = this match
    case StepState.Pending | StepState.Paused | StepState.Failed(_) | StepState.Aborted => true
    case _                                                                              => false
