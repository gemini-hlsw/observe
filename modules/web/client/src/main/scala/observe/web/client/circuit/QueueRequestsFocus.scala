// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import scala.collection.immutable.SortedMap

import cats.Eq
import monocle.Lens
import monocle.macros.Lenses
import observe.model.BatchCommandState
import observe.model.ExecutionQueueView
import observe.model.Observer
import observe.model.QueueId
import observe.model.SequenceView
import observe.model.SequencesQueue
import observe.web.client.model.ObserveAppRootModel
import observe.web.client.model.SessionQueueFilter
import observe.web.client.model.ClientStatus

@Lenses
final case class QueueRequestsFocus(
  clientStatus: ClientStatus,
  sequences:    SequencesQueue[SequenceView],
  seqFilter:    SessionQueueFilter
)

object QueueRequestsFocus {
  implicit val eq: Eq[QueueRequestsFocus] =
    Eq.by(x => (x.clientStatus, x.sequences, x.seqFilter))

  def observers(m: ObserveAppRootModel): SortedMap[QueueId, Observer] =
    SortedMap(ObserveAppRootModel.queuesT.getAll(m).collect {
      case ExecutionQueueView(id, _, BatchCommandState.Run(o, _, _), _, _) =>
        (id, o)
    }: _*)

  val unsafeQueueRequestsFocusL: Lens[ObserveAppRootModel, QueueRequestsFocus] =
    Lens[ObserveAppRootModel, QueueRequestsFocus] { m =>
      val clLens = ClientStatus.clientStatusFocusL
      QueueRequestsFocus(clLens.get(m), m.sequences, ObserveAppRootModel.sessionQueueFilterL.get(m))
    }(v => m => m.copy(clientId = v.clientStatus.clientId, sequences = v.sequences))

}
