// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Eq
import monocle.macros.Lenses
import observe.model.BatchCommandState
import observe.model.Observation

@Lenses
final case class ExecutionQueue(
  name:     String,
  cmdState: BatchCommandState,
  queue:    List[Observation.Id]
)

object ExecutionQueue {
  def init(name: String): ExecutionQueue =
    ExecutionQueue(name, BatchCommandState.Idle, List.empty)

  implicit val eq: Eq[ExecutionQueue] =
    Eq.by(x => (x.name, x.cmdState, x.queue))
}
