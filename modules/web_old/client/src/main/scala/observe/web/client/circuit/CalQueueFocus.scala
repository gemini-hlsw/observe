// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import scala.collection.immutable.SortedMap
import cats._
import cats.implicits.*
import monocle.Getter
import monocle.Lens
import monocle.Traversal
import monocle.function.Each.each
import monocle.function.Each.listEach
import monocle.function.FilterIndex.sortedMapFilterIndex
import observe.model.ExecutionQueueView
import observe.model.Observation
import observe.model.QueueId
import observe.model.QueueManipulationOp
import observe.web.client.components.queue.CalQueueTable
import observe.web.client.model.*
import web.client.table.TableState

final case class CalQueueFocus(
  status:     ClientStatus,
  seqs:       List[CalQueueSeq],
  tableState: TableState[CalQueueTable.TableColumn],
  seqOps:     SortedMap[Observation.Id, QueueSeqOperations],
  lastOp:     Option[QueueManipulationOp]
) {
  val canOperate: Boolean = status.canOperate
  val loggedIn: Boolean   = status.isLogged
}

object CalQueueFocus {
  given Eq[CalQueueFocus] =
    Eq.by(x => (x.status, x.seqs, x.tableState, x.seqOps, x.lastOp))

  def seqQueueOpsT(
    id: Observation.Id
  ): Traversal[CalQueueFocus, QueueSeqOperations] =
    Focus[CalQueueFocus](_.seqOps).andThen(
      sortedMapFilterIndex[Observation.Id, QueueSeqOperations].filterIndex((oid: Observation.Id) =>
        oid === id
      )
    )

  // All metadata of the given obs
  def calSeq(
    id: Observation.Id
  ): Getter[ObserveAppRootModel, Option[CalQueueSeq]] =
    Focus[ObserveAppRootModel](_.sequences).andThen(CalQueueSeq.calQueueSeqG(id))

  def calTS(
    id: QueueId
  ): Lens[ObserveAppRootModel, Option[TableState[CalQueueTable.TableColumn]]] =
    Focus[ObserveAppRootModel](_.uiModel)
      .andThen(ObserveUIModel.appTableStates)
      .andThen(AppTableStates.queueTableAtL(id))

  private def seqOpsL(id: QueueId) =
    Focus[ObserveAppRootModel](_.uiModel)
      .andThen(ObserveUIModel.queues)
      .andThen(CalibrationQueues.calStateSeqOpsT(id))

  private def qLastOpL(id: QueueId) =
    Focus[ObserveAppRootModel](_.uiModel)
      .andThen(ObserveUIModel.queues)
      .andThen(CalibrationQueues.calLastOpO(id))

  // A fairly complicated getter
  def calQueueG(
    id: QueueId
  ): Getter[ObserveAppRootModel, Option[CalQueueFocus]] = {
    // All ids on the queue
    val ids: Traversal[ObserveAppRootModel, Observation.Id] =
      ObserveAppRootModel.executionQueuesT(id).andThen(ExecutionQueueView.queue).andThen(each)

    // combine
    val calQueueSeqG = (s: ObserveAppRootModel) => ids.getAll(s).map(i => calSeq(i).get(s))

    ClientStatus.clientStatusFocusL.asGetter
      .zip(
        Getter(calQueueSeqG).zip(
          calTS(id).asGetter.zip(Getter(seqOpsL(id).getOption).zip(Getter(qLastOpL(id).getOption)))
        )
      ) >>> {
      case (status, (ids, (ts, (seqOps, lastOp)))) =>
        val obsIds = ids.collect { case Some(x) => x }
        CalQueueFocus(status,
                      obsIds,
                      ts.getOrElse(CalQueueTable.State.ROTableState),
                      seqOps.getOrElse(SortedMap.empty),
                      lastOp.flatten
        ).some
      case _                                       =>
        none
    }
  }
}
