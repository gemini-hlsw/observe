// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import cats._
import cats.syntax.all.*
import monocle.Getter
import monocle.Optional
import monocle.Traversal
import monocle.function.At.atSortedMap
import monocle.std
import observe.model.{
  BatchCommandState,
  ExecutionQueueView,
  QueueId,
  SequenceView,
  SequencesQueue
}
import observe.model.enums.BatchExecState
import observe.web.client.model.*

import scala.collection.immutable.SortedMap

final case class CalQueueControlFocus(
  canOperate:  Boolean,
  state:       BatchCommandState,
  execState:   BatchExecState,
  ops:         QueueOperations,
  queueSize:   Int,
  selectedSeq: Int
)

object CalQueueControlFocus {
  given Eq[CalQueueControlFocus] =
    Eq.by(x => (x.canOperate, x.state, x.execState, x.ops, x.queueSize, x.selectedSeq))

  val allQueues: Getter[ObserveAppRootModel, Int] =
    Focus[ObserveAppRootModel](_.sequences).andThen(SequencesQueue.queues[SequenceView])
      .andThen(Getter[SortedMap[QueueId, ExecutionQueueView], Int](_.foldMap(_.queue.size)))

  def optQueue(id: QueueId): Optional[ObserveAppRootModel, QueueOperations] =
    Focus[ObserveAppRootModel](_.uiModel).andThen(ObserveUIModel.queues)
      .andThen(CalibrationQueues.queues)
      .andThen(atSortedMap[QueueId, CalQueueState].at(id))
      .andThen(std.option.some[CalQueueState])
      .andThen(CalQueueState.ops)

  def cmdStateT(
    id: QueueId
  ): Traversal[ObserveAppRootModel, BatchCommandState] =
    ObserveAppRootModel.executionQueuesT(id).andThen(ExecutionQueueView.cmdState)

  def execStateT(id: QueueId): Traversal[ObserveAppRootModel, BatchExecState] =
    ObserveAppRootModel.executionQueuesT(id).andThen(ExecutionQueueView.execState)

  def queueControlG(
    id: QueueId
  ): Getter[ObserveAppRootModel, Option[CalQueueControlFocus]] =
    ClientStatus.canOperateG.zip(
      Getter(optQueue(id).getOption)
        .zip(
          Getter(cmdStateT(id).headOption)
            .zip(
              Getter(execStateT(id).headOption)
                .zip(allQueues.zip(StatusAndLoadedSequencesFocus.filteredSequencesG))
            )
        )
    ) >>> {
      case (status, (Some(c), (Some(s), (Some(e), (qs, f))))) =>
        CalQueueControlFocus(status, s, e, c, qs, f.length).some
      case _                                                  =>
        none[CalQueueControlFocus]
    }
}
