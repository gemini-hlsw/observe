// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.queue

import cats.syntax.all.*
import japgolly.scalajs.react.React
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.react.common.*
import lucuma.react.semanticui.As
import lucuma.react.semanticui.collections.message.Message
import lucuma.react.semanticui.elements.segment.Segment
import lucuma.react.semanticui.elements.segment.SegmentAttached
import lucuma.react.semanticui.modules.tab.TabPane
import observe.model.CalibrationQueueId
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.components.ObserveStyles
import observe.web.client.icons.*
import observe.web.client.model.SectionVisibilityState
import observe.web.client.model.TabSelected
import observe.web.client.reusability.*
import observe.web.client.semanticui.dataTab

final case class CalQueueTabContent(
  canOperate:   Boolean,
  active:       TabSelected,
  logDisplayed: SectionVisibilityState
) extends ReactProps[CalQueueTabContent](CalQueueTabContent.component) {
  protected[queue] val dayCalConnectOps =
    ObserveCircuit.connect(ObserveCircuit.calQueueControlReader(CalibrationQueueId))
  protected[queue] val dayCalConnect    =
    ObserveCircuit.connect(ObserveCircuit.calQueueReader(CalibrationQueueId))

  val isActive: Boolean =
    active === TabSelected.Selected
}

/**
 * Content of the queue tab
 */
object CalQueueTabContent {
  type Props = CalQueueTabContent

  given Reusability[Props] = Reusability.derive[Props]

  private val defaultContent =
    Message(
      icon = true,
      warning = true
    )(
      IconInbox,
      "Work in progress"
    ).render

  private val component = ScalaComponent
    .builder[Props]("CalQueueTabContent")
    .stateless
    .render_P { p =>
      TabPane(active = p.isActive,
              as = As.Segment(Segment(attached = SegmentAttached.Attached, secondary = true)),
              clazz = ObserveStyles.tabSegment
      )(
        dataTab := "daycal",
        React
          .Fragment(
            <.div(ObserveStyles.TabControls,
                  p.dayCalConnectOps(_() match {
                    case Some(x) => CalQueueToolbar(CalibrationQueueId, x)
                    case _       => <.div()
                  }).when(p.canOperate)
            ),
            <.div(ObserveStyles.TabTable,
                  p.dayCalConnect(_() match {
                    case Some(x) =>
                      CalQueueTable(CalibrationQueueId, x)
                    case _       => defaultContent
                  }).when(p.isActive)
            )
          )
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build

  def apply(p: Props): Unmounted[Props, Unit, Unit] =
    component(p)
}
