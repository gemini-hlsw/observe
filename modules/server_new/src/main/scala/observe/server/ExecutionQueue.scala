// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Eq
import lucuma.core.enums.Instrument
import monocle.Focus
import monocle.Lens
import observe.model.BatchCommandState
import observe.model.Observation
import observe.model.SequenceState
import observe.model.enums.Resource
import observe.server.ExecutionQueue.SequenceInQueue

final case class ExecutionQueue(
  name:     String,
  cmdState: BatchCommandState,
  queue:    List[SequenceInQueue]
)

object ExecutionQueue {
  case class SequenceInQueue(
    obsId:      Observation.Id,
    instrument: Instrument,
    state:      SequenceState,
    resources:  Set[Resource | Instrument]
  )

  def init(name: String): ExecutionQueue =
    ExecutionQueue(name, BatchCommandState.Idle, List.empty)

  given Eq[SequenceInQueue] =
    Eq.by(x => (x.obsId, x.state, x.resources))

  given Eq[ExecutionQueue] =
    Eq.by(x => (x.name, x.cmdState, x.queue))

  val cmdState: Lens[ExecutionQueue, BatchCommandState]  = Focus[ExecutionQueue](_.cmdState)
  val name: Lens[ExecutionQueue, String]                 = Focus[ExecutionQueue](_.name)
  val queue: Lens[ExecutionQueue, List[SequenceInQueue]] = Focus[ExecutionQueue](_.queue)
}
