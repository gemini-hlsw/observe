// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import cats.syntax.all._
import diode.ActionHandler
import diode.ActionResult
import diode.ModelRW
import observe.model.QueueManipulationOp
import observe.model.events._
import observe.web.client.actions._
import observe.web.client.model.CalibrationQueues
import observe.web.client.model.RunCalOperation
import observe.web.client.model.StopCalOperation

/**
 * Handles updates to the selected sequences set
 */
class QueueStateHandler[M](modelRW: ModelRW[M, CalibrationQueues])
    extends ActionHandler(modelRW)
    with Handlers[M, CalibrationQueues] {

  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case ServerMessage(QueueUpdated(m @ QueueManipulationOp.Started(qid), _)) =>
      updatedL(
        CalibrationQueues.calLastOpO(qid).replace(m.some) >>>
          CalibrationQueues.runCalL(qid).replace(RunCalOperation.RunCalIdle)
      )

    case ServerMessage(QueueUpdated(m @ QueueManipulationOp.Stopped(qid), _)) =>
      updatedL(
        CalibrationQueues.calLastOpO(qid).replace(m.some) >>>
          CalibrationQueues.stopCalL(qid).replace(StopCalOperation.StopCalIdle)
      )

    case ServerMessage(QueueUpdated(m @ QueueManipulationOp.AddedSeqs(qid, _), _)) =>
      updatedL(CalibrationQueues.calLastOpO(qid).replace(m.some))

    case ServerMessage(QueueUpdated(m @ QueueManipulationOp.Clear(qid), _)) =>
      updatedL(CalibrationQueues.calLastOpO(qid).replace(m.some))

    case ServerMessage(QueueUpdated(m @ QueueManipulationOp.Moved(qid, _, _, _), _)) =>
      updatedL(CalibrationQueues.calLastOpO(qid).replace(m.some))

    case ServerMessage(QueueUpdated(m @ QueueManipulationOp.RemovedSeqs(qid, seqs, _), _)) =>
      updatedL(
        CalibrationQueues.calLastOpO(qid).replace(m.some) >>> CalibrationQueues
          .removeSeqOps(qid, seqs)
      )
  }
}
