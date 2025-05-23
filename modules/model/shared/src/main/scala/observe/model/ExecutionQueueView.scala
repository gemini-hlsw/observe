// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.syntax.all.*
import observe.model.enums.BatchExecState

final case class ExecutionQueueView(
  id:        QueueId,
  name:      String,
  cmdState:  BatchCommandState,
  execState: BatchExecState,
  queue:     List[Observation.Id]
) {
  val observer: Option[Observer] = cmdState match {
    case BatchCommandState.Run(o, _, _) => o.some
    case _                              => none
  }
}

object ExecutionQueueView {
  given Eq[ExecutionQueueView] =
    Eq.by(x => (x.id, x.name, x.cmdState, x.execState, x.queue))

}
