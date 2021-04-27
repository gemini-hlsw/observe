// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import cats._
import cats.syntax.all._
import monocle.Getter
import monocle.Optional
import monocle.Traversal
import monocle.function.At.at
import monocle.macros.Lenses
import monocle.std
import observe.model.BatchCommandState
import observe.model.ExecutionQueueView
import observe.model.QueueId
import observe.model.SequencesQueue
import observe.model.enum.BatchExecState
import observe.web.client.model._

@Lenses
final case class CalQueueControlFocus(
  canOperate:  Boolean,
  state:       BatchCommandState,
  execState:   BatchExecState,
  ops:         QueueOperations,
  queueSize:   Int,
  selectedSeq: Int
)

object CalQueueControlFocus {
  implicit val eq: Eq[CalQueueControlFocus] =
    Eq.by(x => (x.canOperate, x.state, x.execState, x.ops, x.queueSize, x.selectedSeq))

  val allQueues: Getter[ObserveAppRootModel, Int] =
    (ObserveAppRootModel.sequences ^|-> SequencesQueue.queues).asGetter >>> {
      _.foldMap(_.queue.size)
    }

  def optQueue(id: QueueId): Optional[ObserveAppRootModel, QueueOperations] =
    ObserveAppRootModel.uiModel ^|->
      ObserveUIModel.queues ^|->
      CalibrationQueues.queues ^|->
      at(id) ^<-?
      std.option.some ^|->
      CalQueueState.ops

  def cmdStateT(
    id: QueueId
  ): Traversal[ObserveAppRootModel, BatchCommandState] =
    ObserveAppRootModel.executionQueuesT(id) ^|->
      ExecutionQueueView.cmdState

  def execStateT(id: QueueId): Traversal[ObserveAppRootModel, BatchExecState] =
    ObserveAppRootModel.executionQueuesT(id) ^|->
      ExecutionQueueView.execState

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
        none
    }
}
