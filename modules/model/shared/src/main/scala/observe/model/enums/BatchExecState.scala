// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.Show
import lucuma.core.util.Enumerated

sealed abstract class BatchExecState(val tag: String) extends Product with Serializable

object BatchExecState {
  case object Idle
      extends BatchExecState("Idle")      // Queue is not running, and has unfinished sequences.
  case object Running
      extends BatchExecState(
        "Running"
      )                                   // Queue was commanded to run, and at least one sequence is running.
  case object Waiting
      extends BatchExecState(
        "Waiting"
      )                                   // Queue was commanded to run, but it is waiting for resources.
  case object Stopping
      extends BatchExecState(
        "Stopping"
      )                                   // Queue was commanded to stop, but at least one sequence is still running.
  case object Completed
      extends BatchExecState("Completed") // All sequences in the queue were run to completion.

  given Show[BatchExecState] = Show.show(tag)

  def tag(s: BatchExecState): String = s.tag

  /** @group Typeclass Instances */
  given Enumerated[BatchExecState] =
    Enumerated.from(Idle, Running, Waiting, Stopping, Completed).withTag(_.tag)

  extension (v: BatchExecState) {
    def running: Boolean = v match {
      case BatchExecState.Running | BatchExecState.Waiting => true
      case _                                               => false
    }
  }

}
