// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.steps

import cats.data.Nested
import cats.syntax.all._
import cats.Order._
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import react.semanticui.SemanticColor
import react.semanticui.colors._
import react.semanticui.elements.label.Label
import react.semanticui.modules.popup.Popup
import observe.model.Observation
import observe.model.SequenceState
import observe.model.Step
import observe.model.StepId
import observe.model.StepState
import observe.model.dhs.ImageFileId
import observe.model.enum.ActionStatus
import observe.model.enum.Instrument
import observe.model.enum.Resource
import observe.web.client.components.ObserveStyles
import observe.web.client.icons._
import observe.web.client.model.ClientStatus
import observe.web.client.model.ModelOps._
import observe.web.client.model.StepItems._
import observe.web.client.model.StopOperation
import observe.web.client.model.TabOperations
import observe.web.client.reusability._
import observe.web.client.services.HtmlConstants.iconEmpty

/**
 * Component to display the step state and control
 */
final case class StepProgressCell(
  clientStatus: ClientStatus,
  stateSummary: StepStateSummary,
  selectedStep: Option[StepId],
  isPreview:    Boolean
) extends ReactProps[StepProgressCell](StepProgressCell.component) {

  val step: Step                   = stateSummary.step
  val obsId: Observation.Id        = stateSummary.obsId
  val instrument: Instrument       = stateSummary.instrument
  val tabOperations: TabOperations = stateSummary.tabOperations
  val state: SequenceState         = stateSummary.state

  val resourceRunRequested = tabOperations.resourceRunRequested

  def stepSelected(i: StepId): Boolean =
    selectedStep.exists(_ === i) && !isPreview &&
      (clientStatus.isLogged || tabOperations.resourceRunNotIdle(i))

  def isStopping: Boolean =
    tabOperations.stopRequested === StopOperation.StopInFlight
}

object StepProgressCell {
  type Props = StepProgressCell

  implicit val propsReuse: Reusability[Props]                           = Reusability.derive[Props]
  implicit val propsControlButtonResolver: ControlButtonResolver[Props] =
    ControlButtonResolver.build(p => (p.clientStatus, p.state, p.step))

  def labelColor(status: ActionStatus): SemanticColor = status match {
    case ActionStatus.Pending   => Grey
    case ActionStatus.Running   => Yellow
    case ActionStatus.Completed => Green
    case ActionStatus.Paused    => Orange
    case ActionStatus.Failed    => Red
    case ActionStatus.Aborted   => Red
  }

  def labelIcon(status: ActionStatus): VdomNode = status match {
    case ActionStatus.Pending   => iconEmpty
    case ActionStatus.Running   => IconCircleNotched.loading(true)
    case ActionStatus.Completed => IconCheckmark
    case ActionStatus.Paused    => IconPause
    case ActionStatus.Failed    => IconStopCircle
    case ActionStatus.Aborted   => IconStopCircle
  }

  def statusLabel(system: Resource, status: ActionStatus): VdomNode =
    Label(color = labelColor(status))(labelIcon(status), system.show)

  def stepSystemsStatus(step: Step): VdomElement =
    <.div(
      ObserveStyles.configuringRow,
      <.div(
        ObserveStyles.specialStateLabel,
        "Configuring"
      ),
      <.div(
        ObserveStyles.subsystems,
        Step.configStatus
          .getOption(step)
          .orEmpty
          .sortBy(_._1)
          .map(Function.tupled(statusLabel(_, _)))
          .toTagMod
      )
    )

  def stepControlButtons(props: Props): TagMod =
    StepsControlButtons(
      props.obsId,
      props.instrument,
      props.state,
      props.step.id,
      props.step.isObservePaused,
      props.step.isMultiLevel,
      props.tabOperations
    ).when(props.controlButtonsActive)

  def stepObservationStatusAndFile(
    props:  Props,
    fileId: ImageFileId,
    paused: Boolean
  ): VdomElement =
    <.div(
      ObserveStyles.configuringRow,
      if (props.stateSummary.isBias)
        BiasStatus(
          props.obsId,
          props.step.id,
          fileId,
          stopping = !paused && props.isStopping,
          paused
        )
      else
        props.stateSummary.nsStatus.fold[VdomElement] {
          ObservationProgressBar(props.obsId,
                                 props.step.id,
                                 fileId,
                                 stopping = !paused && props.isStopping,
                                 paused
          )
        } { nsStatus =>
          NodAndShuffleProgressMessage(props.obsId,
                                       props.step.id,
                                       fileId,
                                       props.isStopping,
                                       paused,
                                       nsStatus
          )
        },
      stepControlButtons(props)
    )

  def stepObservationPausing(props: Props): VdomElement =
    <.div(
      ObserveStyles.configuringRow,
      <.div(
        ObserveStyles.specialStateLabel,
        props.state.show
      ),
      stepControlButtons(props)
    )

  private def textWithPopup(text: String): VdomNode =
    Popup(
      trigger = <.span(text)
    )(text)

  def stepSubsystemControl(props: Props): VdomElement =
    <.div(
      ObserveStyles.configuringRow,
      RunFromStep(
        props.obsId,
        props.step.id,
        props.tabOperations.resourceInFlight(props.step.id),
        props.tabOperations.startFromRequested
      ).when(!props.step.isFinished && props.clientStatus.canOperate),
      <.div(
        ObserveStyles.specialStateLabel,
        if (props.stateSummary.isAC) {
          if (props.stateSummary.isACRunning) {
            "Running Align & Calib..."
          } else if (props.stateSummary.anyError) {
            textWithPopup(props.step.show)
          } else {
            "Align & Calib"
          }
        } else {
          textWithPopup(props.step.show)
        }
      ),
      SubsystemControlCell(
        props.obsId,
        props.step.id,
        Nested(Step.configStatus.getOption(props.step)).map(_._1).value.orEmpty,
        props.resourceRunRequested,
        props.clientStatus.canOperate
      )
    )

  def stepPaused(props: Props): VdomElement =
    <.div(
      ObserveStyles.configuringRow,
      props.step.show
    )

  def stepDisplay(props: Props): VdomElement =
    (props.state, props.step) match {
      case (f, s) if s.status === StepState.Running && s.fileId.isEmpty && f.userStopRequested =>
        // Case pause at the sequence level
        stepObservationPausing(props)
      case (_, s) if s.status === StepState.Running && s.fileId.isEmpty                        =>
        // Case configuring, label and status icons
        stepSystemsStatus(s)
      case (_, s) if s.isObservePaused && s.fileId.isDefined                                   =>
        // Case for exposure paused, label and control buttons
        stepObservationStatusAndFile(props, s.fileId.orEmpty, paused = true)
      case (_, s) if s.status === StepState.Running && s.fileId.isDefined                      =>
        // Case for a exposure onging, progress bar and control buttons
        stepObservationStatusAndFile(props, s.fileId.orEmpty, paused = false)
      case (_, s) if s.wasSkipped                                                              =>
        <.p("Skipped")
      case (_, _) if props.step.skip                                                           =>
        <.p("Skip")
      case (_, s) if s.status === StepState.Completed && s.fileId.isDefined                    =>
        <.p(ObserveStyles.componentLabel, s.fileId.orEmpty)
      case (_, s) if props.stepSelected(s.id) && s.canConfigure                                =>
        stepSubsystemControl(props)
      case _                                                                                   =>
        <.p(ObserveStyles.componentLabel, props.step.show)
    }

  protected val component = ScalaComponent
    .builder[Props]("StepProgressCell")
    .stateless
    .render_P(stepDisplay)
    .configure(Reusability.shouldComponentUpdate)
    .build
}
