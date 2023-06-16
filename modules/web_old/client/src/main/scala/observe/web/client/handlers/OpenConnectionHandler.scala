// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import cats.syntax.all._
import diode.ActionHandler
import diode.ActionResult
import diode.ModelRW
import observe.model.events._
import observe.web.client.actions._
import observe.web.client.components.queue.CalQueueTable
import observe.web.client.model.CalibrationQueues

/**
 * Updates internal states when the connection opens
 */
class OpenConnectionHandler[M](modelRW: ModelRW[M, CalibrationQueues])
    extends ActionHandler(modelRW)
    with Handlers[M, CalibrationQueues] {

  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case ServerMessage(ConnectionOpenEvent(u, _, _)) =>
      val ts = u
        .as(CalQueueTable.State.EditableTableState)
        .getOrElse(CalQueueTable.State.ROTableState)
      updatedL(CalibrationQueues.tableStatesT.replace(ts))
  }
}
