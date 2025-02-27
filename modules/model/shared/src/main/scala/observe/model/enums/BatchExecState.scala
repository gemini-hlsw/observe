// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.Show
import lucuma.core.util.Enumerated

enum BatchExecState(val tag: String) derives Enumerated {
  case Idle extends BatchExecState("Idle") // Queue is not running, and has unfinished sequences.
  case Running
      extends BatchExecState(
        "Running"
      )                                   // Queue was commanded to run, and at least one sequence is running.
  case Waiting
      extends BatchExecState(
        "Waiting"
      )                                   // Queue was commanded to run, but it is waiting for resources.
  case Stopping
      extends BatchExecState(
        "Stopping"
      )                                   // Queue was commanded to stop, but at least one sequence is still running.
  case Completed
      extends BatchExecState("Completed") // All sequences in the queue were run to completion.
}

object BatchExecState {
  given Show[BatchExecState] = Show.show(_.tag)

  extension (v: BatchExecState) {
    def running: Boolean = v match {
      case BatchExecState.Running | BatchExecState.Waiting => true
      case _                                               => false
    }
  }

}
