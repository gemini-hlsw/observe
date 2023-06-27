// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import cats.Eq
import cats.data.NonEmptyList
import cats.syntax.all.*
import monocle.Getter
import observe.web.client.model.*

final case class TabFocus(
  canOperate:  Boolean,
  tabs:        NonEmptyList[Either[CalibrationQueueTabActive, AvailableTab]],
  displayName: Option[String]
)

object TabFocus {
  given Eq[TabFocus] =
    Eq.by(x => (x.canOperate, x.tabs, x.displayName))

  val tabFocusG: Getter[ObserveAppRootModel, TabFocus] = {
    val getter = Focus[ObserveAppRootModel](_.uiModel).andThen(
      Focus[ObserveUIModel](_.sequencesOnDisplay)
        .andThen(SequencesOnDisplay.availableTabsG)
        .zip(ObserveUIModel.displayNameG)
    )
    ClientStatus.canOperateG.zip(getter) >>> { case (o, (t, ob)) =>
      TabFocus(o, t, ob)
    }
  }

}
