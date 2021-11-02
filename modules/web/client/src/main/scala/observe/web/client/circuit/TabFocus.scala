// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import cats.Eq
import cats.data.NonEmptyList
import cats.syntax.all._
import monocle.Getter
import observe.model._
import observe.web.client.model._

final case class TabFocus(
  canOperate: Boolean,
  tabs:       NonEmptyList[Either[CalibrationQueueTabActive, AvailableTab]]
  // defaultObserver: Observer
)

object TabFocus {
  implicit val eq: Eq[TabFocus] =
    Eq.by(x => (x.canOperate, x.tabs)) //, x.defaultObserver))

  val tabFocusG: Getter[ObserveAppRootModel, TabFocus] = {
    val getter = ObserveAppRootModel.uiModel.andThen(
      (ObserveUIModel.sequencesOnDisplay
        .andThen(SequencesOnDisplay.availableTabsG))
      //  .zip(ObserveUIModel.defaultObserverG)
    )
    ClientStatus.canOperateG.zip(getter) >>> { case (o, t) =>
      TabFocus(o, t) //, ob)
    }
  }

}
