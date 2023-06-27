// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.tabs

import cats.syntax.all.*
import japgolly.scalajs.react.ReactMonocle.*
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.*
import japgolly.scalajs.react.component.builder.Lifecycle.RenderScope
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import react.common.*
import react.semanticui.colors.*
import react.semanticui.elements.label.Label
import observe.model.CalibrationQueueId
import observe.model.enums.BatchExecState
import observe.web.client.actions.RequestAddSeqCal
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.components.ObserveStyles
import observe.web.client.icons.*
import observe.web.client.model.CalibrationQueueTabActive
import observe.web.client.model.Pages.*
import observe.web.client.model.TabSelected
import observe.web.client.reusability.*
import observe.web.client.semanticui.*

final case class CalibrationQueueTab(
  router: RouterCtl[ObservePages],
  tab:    CalibrationQueueTabActive
) extends ReactProps[CalibrationQueueTab](CalibrationQueueTab.component)

object CalibrationQueueTab {
  type Props = CalibrationQueueTab

  type Backend = RenderScope[Props, State, Unit]

    final case class State(draggingOver: Option[String]) {
    val onDrag: Boolean = draggingOver.isDefined
  }

  given Reusability[Props] =
    Reusability.by(x => (x.tab.active, x.tab.calibrationTab.state))
  given Reusability[State] = Reusability.derive[State]

  def showCalibrationQueue(p: Props, page: ObservePages)(e: ReactEvent): Callback =
    // prevent default to avoid the link jumping
    e.preventDefaultCB *>
      // Request to display the selected sequence
      p.router
        .setUrlAndDispatchCB(page)
        .unless(p.tab.active === TabSelected.Selected)
        .void

  def addToQueueE(e: ReactDragEvent): Callback =
    e.preventDefaultCB *>
      Option(e.dataTransfer.getData("text/plain"))
        .flatMap(lucuma.core.model.Observation.Id.parse)
        .map(id => ObserveCircuit.dispatchCB(RequestAddSeqCal(CalibrationQueueId, id)))
        .getOrEmpty

  private def onDragEnter(b: Backend)(e: ReactDragEvent) =
    b.setStateL(State.draggingOver)(Option(e.dataTransfer.getData("text/plain")))

  private def onDrop(b: Backend)(e: ReactDragEvent) =
    addToQueueE(e) *>
      onDragEnd(b)

  private def onDragEnd(b: Backend) =
    b.setStateL(State.draggingOver)(none)

  private def linkTo(b: Backend, page: ObservePages)(mod: TagMod*) = {
    val p      = b.props
    val active = p.tab.active

    <.a(
      ^.href  := p.router.urlFor(page).value,
      ^.onClick ==> showCalibrationQueue(p, page),
      ^.cls   := "item",
      ^.classSet(
        "active" -> (active === TabSelected.Selected)
      ),
      ObserveStyles.tab,
      dataTab := "daycalqueue",
      ObserveStyles.inactiveTabContent.when(active === TabSelected.Background),
      ObserveStyles.activeTabContent
        .when(active === TabSelected.Selected)
        .unless(b.state.onDrag),
      ObserveStyles.dropOnTab.when(b.state.onDrag),
      ^.onDragOver ==> { (e: ReactDragEvent) =>
        e.preventDefaultCB *> Callback { e.dataTransfer.dropEffect = "copy" }
      },
      ^.onDragEnter ==> onDragEnter(b) _,
      ^.onDrop ==> onDrop(b) _,
      ^.onDragEnd --> onDragEnd(b),
      ^.onDragLeave --> onDragEnd(b),
      mod.toTagMod
    )
  }

  val component = ScalaComponent
    .builder[Props]("CalibrationQueueTab")
    .initialState(State(None))
    .render { b =>
      val tab  = b.props.tab
      val icon = tab.calibrationTab.state match {
        case BatchExecState.Running   =>
          IconCircleNotched.loading()
        case BatchExecState.Completed => IconCheckmark
        case _                        => IconSelectedRadio
      }

      val color = tab.calibrationTab.state match {
        case BatchExecState.Running   => Orange
        case BatchExecState.Completed => Green
        case _                        => Grey
      }

      val tabContent: VdomNode =
        <.div(
          ObserveStyles.TabLabel,
          ObserveStyles.LoadedTab,
          <.div(ObserveStyles.activeInstrumentLabel, "Daytime Queue"),
          Label(color = color, clazz = ObserveStyles.labelPointer)(
            icon,
            tab.calibrationTab.state.show
          )
        )

      linkTo(b, CalibrationQueuePage)(tabContent)
    }
    .configure(Reusability.shouldComponentUpdate)
    .build
}
