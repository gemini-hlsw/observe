// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import cats.Eq
import monocle.Getter
import monocle.macros.Lenses
import observe.model.Observation
import observe.model.QueueId
import observe.model._
import observe.model.enum.Instrument
import observe.web.client.model._

final case class SequenceObserverFocus(
  instrument: Instrument,
  obsId:      Observation.Id,
  completed:  Boolean,
  observer:   Option[Observer]
)

object SequenceObserverFocus {
  implicit val eq: Eq[SequenceObserverFocus] =
    Eq.by(x => (x.instrument, x.obsId, x.completed, x.observer))
}

final case class DayCalObserverFocus(queueId: QueueId, observer: Option[Observer])

object DayCalObserverFocus {
  implicit val eq: Eq[DayCalObserverFocus] =
    Eq.by(x => (x.queueId, x.observer))
}

@Lenses
final case class HeaderSideBarFocus(
  status:     ClientStatus,
  conditions: Conditions,
  operator:   Option[Operator],
  observer:   Either[Observer, Either[DayCalObserverFocus, SequenceObserverFocus]]
)

object HeaderSideBarFocus {
  implicit val eq: Eq[HeaderSideBarFocus] =
    Eq.by(x => (x.status, x.conditions, x.operator, x.observer))

  val headerSideBarG: Getter[ObserveAppRootModel, HeaderSideBarFocus] =
    Getter[ObserveAppRootModel, HeaderSideBarFocus] { c =>
      val clientStatus = ClientStatus(c.uiModel.user, c.ws)
      val obs          = c.uiModel.sequencesOnDisplay.selectedObserver
        .toRight(c.uiModel.defaultObserver)
      HeaderSideBarFocus(clientStatus, c.sequences.conditions, c.sequences.operator, obs)
    }
}
