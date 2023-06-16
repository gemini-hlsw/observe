// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import cats.Eq
import cats.data.NonEmptyList
import cats.syntax.all._
import monocle.Getter
import observe.model.{Observation, StepId}
import observe.model.enum._
import observe.web.client.model._

sealed trait TabContentFocus extends Product with Serializable {
  val canOperate: Boolean
  val logDisplayed: SectionVisibilityState
  val active: TabSelected
  def isActive: Boolean = active === TabSelected.Selected
}

object TabContentFocus {
  implicit val eq: Eq[TabContentFocus] =
    Eq.instance {
      case (a: SequenceTabContentFocus, b: SequenceTabContentFocus) => a === b
      case (a: CalQueueTabContentFocus, b: CalQueueTabContentFocus) => a === b
      case _                                                        => false
    }

  val tabContentFocusG: Getter[ObserveAppRootModel, NonEmptyList[TabContentFocus]] = {
    val getter = ObserveAppRootModel.logDisplayL.asGetter
      .zip(ObserveAppRootModel.sequencesOnDisplayL.asGetter)
    ClientStatus.canOperateG.zip(getter) >>> { p =>
      val (o, (log, SequencesOnDisplay(tabs))) = p
      NonEmptyList.fromListUnsafe(tabs.withFocus.toList.collect {
        case (tab: SequenceTab, active)       =>
          SequenceTabContentFocus(
            o,
            tab.instrument,
            tab.sequence.idName.id,
            TabSelected.fromBoolean(active),
            StepsTableTypeSelection.fromStepId(tab.stepConfigDisplayed),
            log,
            tab.isPreview,
            tab.sequence.steps.map(_.id)
          )
        case (_: CalibrationQueueTab, active) =>
          CalQueueTabContentFocus(o, TabSelected.fromBoolean(active), log)
      })
    }
  }
}

final case class SequenceTabContentFocus(
  canOperate:   Boolean,
  instrument:   Instrument,
  id:           Observation.Id,
  active:       TabSelected,
  tableType:    StepsTableTypeSelection,
  logDisplayed: SectionVisibilityState,
  isPreview:    Boolean,
  steps:        List[StepId]
) extends TabContentFocus {
  val hasControls: Boolean = canOperate && !isPreview
}

object SequenceTabContentFocus {
  implicit val eq: Eq[SequenceTabContentFocus] =
    Eq.by(x =>
      (x.canOperate,
       x.instrument,
       x.id,
       x.active,
       x.tableType,
       x.logDisplayed,
       x.isPreview,
       x.steps
      )
    )
}

final case class CalQueueTabContentFocus(
  canOperate:   Boolean,
  active:       TabSelected,
  logDisplayed: SectionVisibilityState
) extends TabContentFocus

object CalQueueTabContentFocus {
  implicit val eq: Eq[CalQueueTabContentFocus] =
    Eq.by(x => (x.canOperate, x.active, x.logDisplayed))
}
