// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import react.common.*
import observe.ui.ObserveStyles
import observe.ui.Icons

case class SettingsCell(
  // ctl:        RouterCtl[Pages.ObservePages],
  instrument: Instrument,
  obsId:      Observation.Id,
  stepId:     Step.Id,
  isPreview:  Boolean
) extends ReactFnProps(SettingsCell.component)

object SettingsCell:
  private type Props = SettingsCell

  protected val component = ScalaFnComponent { props =>
    // val page = if (props.isPreview) {
    //   Pages.PreviewConfigPage(p.instrument, p.obsId, p.stepId)
    // } else {
    //   Pages.SequenceConfigPage(p.instrument, p.obsId, p.stepId)
    // }
    <.div(ObserveStyles.StepSettingsHeader)(
      // p.ctl.link(page)(
      Icons.CaretRight
        .withFixedWidth() // (^.onClick --> p.ctl.setUrlAndDispatchCB(page))
        // )
    )
  }
