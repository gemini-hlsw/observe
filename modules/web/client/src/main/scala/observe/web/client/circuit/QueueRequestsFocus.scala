// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import scala.collection.immutable.SortedMap

import cats.Eq
import monocle.Lens
import monocle.Optional
import monocle.macros.Lenses
import observe.model.BatchCommandState
import observe.model.ClientId
import observe.model.ExecutionQueueView
import observe.model.Observer
import observe.model.QueueId
import observe.model.SequenceView
import observe.model.SequencesQueue
import observe.web.client.model.ObserveAppRootModel
import observe.web.client.model.ObserveUIModel
import observe.web.client.model.SequencesOnDisplay
import observe.web.client.model.SessionQueueFilter

@Lenses
final case class QueueRequestsFocus(
  clientId:       Option[ClientId],
  sequences:      SequencesQueue[SequenceView],
  calTabObserver: Option[Observer],
  queuesObserver: SortedMap[QueueId, Observer],
  seqFilter:      SessionQueueFilter
)

object QueueRequestsFocus {
  implicit val eq: Eq[QueueRequestsFocus] =
    Eq.by(x => (x.clientId, x.sequences, x.calTabObserver, x.queuesObserver))

  def observers(m: ObserveAppRootModel): SortedMap[QueueId, Observer] =
    SortedMap(ObserveAppRootModel.queuesT.getAll(m).collect {
      case ExecutionQueueView(id, _, BatchCommandState.Run(o, _, _), _, _) =>
        (id, o)
    }: _*)

  val calTabObserverL: Optional[ObserveAppRootModel, Observer] =
    ObserveAppRootModel.uiModel ^|->
      ObserveUIModel.sequencesOnDisplay ^|-?
      SequencesOnDisplay.calTabObserver

  // This lens is read only but a getter is not usable in diode
  val unsafeQueueRequestsFocusL: Lens[ObserveAppRootModel, QueueRequestsFocus] =
    Lens[ObserveAppRootModel, QueueRequestsFocus](m =>
      QueueRequestsFocus(m.clientId,
                         m.sequences,
                         calTabObserverL.getOption(m),
                         observers(m),
                         ObserveAppRootModel.sessionQueueFilterL.get(m)
      )
    )(v => m => m.copy(clientId = v.clientId, sequences = v.sequences))

}
