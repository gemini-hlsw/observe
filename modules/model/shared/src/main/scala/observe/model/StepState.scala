// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.syntax.all.*

sealed abstract class StepState extends Product with Serializable

object StepState {

  case object Pending                  extends StepState
  case object Completed                extends StepState
  case object Skipped                  extends StepState
  case object Aborted                  extends StepState
  final case class Failed(msg: String) extends StepState
  case object Running                  extends StepState
  case object Paused                   extends StepState

  given Eq[StepState] =
    Eq.instance {
      case (Pending, Pending)     => true
      case (Completed, Completed) => true
      case (Skipped, Skipped)     => true
      case (Failed(a), Failed(b)) => a === b
      case (Running, Running)     => true
      case (Paused, Paused)       => true
      case (Aborted, Aborted)     => true
      case _                      => false
    }

  extension (s: StepState) {
    def canSetBreakpoint: Boolean = s match {
      case StepState.Pending | StepState.Skipped | StepState.Paused | StepState.Running |
          StepState.Aborted =>
        true
      case _ => false
    }

    def canSetSkipmark: Boolean = s match {
      case StepState.Pending | StepState.Paused | StepState.Aborted => true
      case _ if hasError                                            => true
      case _                                                        => false
    }

    def hasError: Boolean = s match {
      case StepState.Failed(_) => true
      case _                   => false
    }

    def isRunning: Boolean = s === StepState.Running

    def isPending: Boolean = s === StepState.Pending

    def runningOrComplete: Boolean = s match {
      case StepState.Running | StepState.Completed => true
      case _                                       => false
    }

    def isFinished: Boolean = s match {
      case StepState.Completed | StepState.Skipped => true
      case _                                       => false
    }

    def wasSkipped: Boolean = s === StepState.Skipped

    def canConfigure: Boolean = s match {
      case StepState.Pending | StepState.Paused | StepState.Failed(_) | StepState.Aborted => true
      case _                                                                              => false
    }

  }
}
