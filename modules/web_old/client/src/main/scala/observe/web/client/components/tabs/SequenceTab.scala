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
import react.common.{Size => _, _}
import react.semanticui.colors.*
import react.semanticui.elements.button.Button
import react.semanticui.elements.icon.*
import react.semanticui.elements.label.Label
import react.semanticui.modules.popup.Popup
import react.semanticui.sizes.*
import observe.model.Observation
import observe.model.Observer
import observe.model.RunningStep
import observe.model.SequenceState
import observe.model.SystemOverrides
import observe.model.enums.Instrument
import observe.model.enums.Resource
import observe.web.client.actions.LoadSequence
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.components.ObserveStyles
import observe.web.client.icons.*
import observe.web.client.model.AvailableTab
import observe.web.client.model.Pages.*
import observe.web.client.model.ResourceRunOperation
import observe.web.client.model.TabSelected
import observe.web.client.reusability.*
import observe.web.client.semanticui.*

final case class SequenceTab(
  router:             RouterCtl[ObservePages],
  tab:                AvailableTab,
  loggedIn:           Boolean,
  displayName:        Option[String],
  systemOverrides:    SystemOverrides,
  runningInstruments: List[Instrument]
) extends ReactProps[SequenceTab](SequenceTab.component)

object SequenceTab {
  type Props = SequenceTab

    final case class State(loading: Boolean, prevTabId: Observation.IdName, prevTabLoading: Boolean)

  given Reusability[Props] =
    Reusability.caseClassExcept[Props]("router")
  given Reusability[State] = Reusability.by(_.loading)

  type Backend = RenderScope[Props, State, Unit]

  def load(
    b:      Backend,
    inst:   Instrument,
    idName: Observation.IdName
  ): (ReactMouseEvent, Button.ButtonProps) => Callback =
    (e: ReactMouseEvent, _: Button.ButtonProps) =>
      e.preventDefaultCB *>
        e.stopPropagationCB *>
        b.setStateL(State.loading)(true).when(b.props.displayName.isDefined) *>
        b.props.displayName
          .map(d => ObserveCircuit.dispatchCB(LoadSequence(Observer(d), inst, idName)))
          .getOrEmpty

  private def showSequence(p: Props, page: ObservePages)(e: ReactEvent): Callback =
    // prevent default to avoid the link jumping
    e.preventDefaultCB *>
      // Request to display the selected sequence
      p.router
        .setUrlAndDispatchCB(page)
        .unless(p.tab.active === TabSelected.Selected)
        .void

  private def linkTo(p: Props, page: ObservePages)(mod: TagMod*) = {
    val active     = p.tab.active
    val isPreview  = p.tab.isPreview
    val instrument = p.tab.instrument
    val dataId     = if (isPreview) "preview" else instrument.show
    val hasError   = p.tab.status.isError

    <.a(
      ^.href  := p.router.urlFor(page).value,
      ^.onClick ==> showSequence(p, page),
      ^.cls   := "item",
      ^.classSet(
        "active" -> (active === TabSelected.Selected)
      ),
      IconAttention.color(Red).when(hasError),
      ObserveStyles.tab,
      ObserveStyles.inactiveTabContent.when(active === TabSelected.Background),
      ObserveStyles.activeTabContent.when(active === TabSelected.Selected),
      ObserveStyles.errorTab.when(hasError),
      dataTab := dataId,
      mod.toTagMod
    )
  }

  val component = ScalaComponent
    .builder[Props]
    .initialStateFromProps(props => State(false, props.tab.idName, props.tab.loading))
    .render { b =>
      val status         = b.props.tab.status
      val sequenceIdName = b.props.tab.idName
      val instrument     = b.props.tab.instrument
      val running        = b.props.runningInstruments.contains(instrument)
      val isPreview      = b.props.tab.isPreview
      val resources      = b.props.tab.resourceOperations.filterNot { case (r, s) =>
        r.isInstrument || s === ResourceRunOperation.ResourceRunIdle
      }
      val instName       = instrument.show
      val dispName       = if (isPreview) s"Preview: $instName" else instName
      val isLogged       = b.props.loggedIn
      val nextStepToRun  = StepIdDisplayed(b.props.tab.nextStepToRun)

      val tabTitle = b.props.tab.runningStep match {
        case Some(RunningStep(_, last, total)) =>
          s"${sequenceIdName.name} - ${last + 1}/$total"
        case _                                 =>
          sequenceIdName.name
      }

      val icon: Icon = status match {
        case SequenceState.Running(_, _) =>
          IconCircleNotched.loading()
        case SequenceState.Completed     => IconCheckmark
        case _                           => IconSelectedRadio
      }

      val color = status match {
        case SequenceState.Running(_, _) => Orange
        case SequenceState.Completed     => Green
        case _                           => Grey
      }

      val linkPage: ObservePages =
        if (isPreview) {
          PreviewPage(instrument, sequenceIdName.id, nextStepToRun)
        } else {
          SequencePage(instrument, sequenceIdName.id, nextStepToRun)
        }

      val loadButton: TagMod =
        Popup(
          content = s"Load sequence ${sequenceIdName.name}",
          trigger = Button(
            size = Large,
            clazz = ObserveStyles.LoadButton,
            compact = true,
            icon = IconUpload,
            color = Teal,
            disabled = b.state.loading || running,
            loading = b.state.loading,
            onClickE = load(b, instrument, sequenceIdName)
          )
        ).when(isPreview && isLogged)

      val disabledSubsystems =
        <.div(
          ObserveStyles.ResourceLabels,
          List(
            ("TCS", b.props.systemOverrides.isTcsEnabled),
            ("GCAL", b.props.systemOverrides.isGcalEnabled),
            ("DHS", b.props.systemOverrides.isDhsEnabled),
            ("INST", b.props.systemOverrides.isInstrumentEnabled)
          ).map { case (l, b) =>
            <.div(ObserveStyles.DisabledSubsystem, l).unless(b)
          }.toTagMod
        )

      val resourceLabels =
        <.div(
          ObserveStyles.resourceLabels,
          resources.map { case (r, s) =>
            val show  = r match {
              case Resource.TCS  => b.props.systemOverrides.isTcsEnabled
              case Resource.Gcal => b.props.systemOverrides.isGcalEnabled
              case _: Instrument => b.props.systemOverrides.isInstrumentEnabled
              case _             => true
            }
            val color = s match {
              case ResourceRunOperation.ResourceRunIdle         => Blue // Unused
              case ResourceRunOperation.ResourceRunCompleted(_) => Green
              case ResourceRunOperation.ResourceRunInFlight(_)  => Yellow
              case ResourceRunOperation.ResourceRunFailed(_)    => Red
            }
            (s match {
              case ResourceRunOperation.ResourceRunInFlight(_)  =>
                Label(color = color, size = Small, clazz = ObserveStyles.activeResourceLabel)(
                  r.show
                ): VdomNode
              case ResourceRunOperation.ResourceRunCompleted(_) =>
                Label(color = color, size = Small)(r.show): VdomNode
              case _                                            => EmptyVdom
            }).when(show)
          }.toTagMod
        )

      val tab =
        <.div(
          ObserveStyles.TabLabel,
          ObserveStyles.PreviewTab.when(isLogged),
          ObserveStyles.LoadedTab.when(!isPreview),
          <.div(
            ObserveStyles.TabTitleRow,
            dispName,
            disabledSubsystems.when(!isPreview),
            resourceLabels.when(!isPreview)
          ),
          Label(color = color, clazz = ObserveStyles.labelPointer)(icon, tabTitle).when(!isPreview),
          loadButton.when(isPreview)
        )

      linkTo(b.props, linkPage)(tab)
    }
    .getDerivedStateFromProps { (props, state) =>
      val preview = props.tab.isPreview
      val id      = state.prevTabId
      val newId   = props.tab.idName

      val wasLoading = state.prevTabLoading
      val isLoading  = props.tab.loading
      // Reset the loading state if the id changes
      Function.chain(
        State.loading
          .replace(false)
          .some
          .filter(_ => preview && (id =!= newId || (wasLoading && !isLoading)))
          .toList :::
          List(
            Focus[State](_.prevTabId).replace(newId),
            Focus[State](_.prevTabLoading).replace(isLoading)
          )
      )(state)
    }
    .configure(Reusability.shouldComponentUpdate)
    .build
}
