// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import cats.syntax.all._
import diode.ActionHandler
import diode.ActionResult
import diode.ModelRW
import observe.model.ClientId
import observe.model.QueueId
import observe.web.client.actions._
import observe.web.client.circuit.QueueRequestsFocus
import observe.web.client.services.ObserveWebClient

/**
 * Handles actions sending requests to the backend
 *
 * Note this handler is based on an unsafe lens. Don't change the model from here
 */
class QueueRequestsHandler[M](modelRW: ModelRW[M, QueueRequestsFocus])
    extends ActionHandler(modelRW)
    with Handlers[M, QueueRequestsFocus] {

  def handleAddAllDayCal: PartialFunction[Any, ActionResult[M]] = {
    case RequestAllSelectedSequences(qid) =>
      val ids = value.seqFilter.filterS(value.sequences.sessionQueue).map(_.idName.id)
      effectOnly(
        requestEffect(qid,
                      ObserveWebClient.addSequencesToQueue(ids, _),
                      AllDayCalCompleted.apply,
                      AllDayCalFailed.apply
        )
      )
  }

  def handleAddDayCal: PartialFunction[Any, ActionResult[M]] = { case RequestAddSeqCal(qid, oid) =>
    effectOnly(
      requestEffect(qid,
                    ObserveWebClient.addSequenceToQueue(oid, _),
                    AllDayCalCompleted.apply,
                    AllDayCalFailed.apply
      )
    )
  }

  def handleClearAllCal: PartialFunction[Any, ActionResult[M]] = { case RequestClearAllCal(qid) =>
    effectOnly(
      requestEffect(qid,
                    ObserveWebClient.clearQueue,
                    ClearAllCalCompleted.apply,
                    ClearAllCalFailed.apply
      )
    )
  }

  def handleRunCal: PartialFunction[Any, ActionResult[M]] = { case RequestRunCal(qid) =>
    (value.clientStatus.clientId, value.clientStatus.observer)
      .mapN { (cid, obs) =>
        effectOnly(
          requestEffect2((qid, cid),
                         ObserveWebClient.runQueue(_: QueueId, _: ClientId, obs),
                         RunCalCompleted.apply,
                         RunCalFailed.apply
          )
        )
      }
      .getOrElse(noChange)
  }

  def handleStopCal: PartialFunction[Any, ActionResult[M]] = { case RequestStopCal(qid) =>
    value.clientStatus.clientId
      .map { cid =>
        effectOnly(
          requestEffect2((qid, cid),
                         ObserveWebClient.stopQueue,
                         StopCalCompleted.apply,
                         StopCalFailed.apply
          )
        )
      }
      .getOrElse(noChange)
  }

  def handleRemoveSeqCal: PartialFunction[Any, ActionResult[M]] = {
    case RequestRemoveSeqCal(qid, id) =>
      value.clientStatus.clientId
        .map { _ =>
          effectOnly(
            requestEffect2((qid, id),
                           ObserveWebClient.removeSequenceFromQueue,
                           RemoveSeqCalCompleted.apply,
                           RemoveSeqCalFailed.apply(_: QueueId, id)
            )
          )
        }
        .getOrElse(noChange)
  }

  def handleMoveCal: PartialFunction[Any, ActionResult[M]] = { case RequestMoveCal(qid, oid, i) =>
    value.clientStatus.clientId
      .map { cid =>
        effectOnly(
          requestEffect2((qid, cid),
                         ObserveWebClient.moveSequenceQueue(_: QueueId, oid.id, i, _: ClientId),
                         MoveCalCompleted.apply,
                         MoveCalFailed.apply(_: QueueId, oid)
          )
        )
      }
      .getOrElse(noChange)
  }

  override def handle: PartialFunction[Any, ActionResult[M]] =
    List(handleAddAllDayCal,
         handleAddDayCal,
         handleClearAllCal,
         handleRunCal,
         handleStopCal,
         handleMoveCal,
         handleRemoveSeqCal
    ).combineAll

}
