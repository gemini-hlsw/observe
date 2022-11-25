// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import alleycats.Empty
import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.syntax.display.*
import observe.model.ClientStatus
import observe.model.ExecutionStep
import observe.model.ImageFileId
import observe.model.NodAndShuffleStatus
import observe.model.NodAndShuffleStep
import observe.model.enums.ExecutionStepType
import observe.model.enums.SequenceState
import observe.model.enums.StepState
import observe.ui.ObserveStyles
import observe.ui.model.StopOperation
import observe.ui.model.TabOperations
import observe.ui.model.extensions.*
import react.common.*
import crystal.react.View

case class StepProgressCell(
  clientStatus:  ClientStatus,
  step:          View[ExecutionStep],
  stepIndex:     Int,
  obsId:         Observation.Id,
  instrument:    Instrument,
  tabOperations: TabOperations,
  sequenceState: SequenceState,
  selectedStep:  Option[Step.Id],
  isPreview:     Boolean
) extends ReactFnProps(StepProgressCell.component):
  def stepSelected(i: Step.Id): Boolean =
    selectedStep.exists(_ === i) && !isPreview &&
      (clientStatus.isLogged || tabOperations.resourceRunNotIdle(i))

  val isAC: Boolean =
    step.get.alignAndCalib(instrument).isDefined

  val isNS: Boolean =
    step.get.nodAndShuffle(instrument).isDefined

  private val isRunning =
    tabOperations.resourceInFlight(step.get.id) || step.get.isRunning

  val isACRunning: Boolean =
    isAC && isRunning

  val isNSRunning: Boolean =
    isNS && isRunning

  val isNSObserving: Boolean =
    isNS && step.get.isRunning

  val anyError: Boolean =
    tabOperations.resourceInError(step.get.id) || step.get.hasError

  val isACInError: Boolean =
    isAC && anyError

  val isNSInError: Boolean =
    isNS && anyError

  val isBias: Boolean =
    step.get.stepType(instrument).exists(_ === ExecutionStepType.Bias)

  def canControlThisStep(selected: Option[Step.Id], hasControls: Boolean): Boolean =
    hasControls && selected.exists(_ === step.get.id)

  // def detailRows(selected: Option[StepId], hasControls: Boolean): DetailRows =
  //   if (((isNS || isNSInError) && canControlThisStep(selected, hasControls)) || isNSObserving)
  //     DetailRows.TwoDetailRows
  //   else if (isACRunning || isACInError)
  //     DetailRows.OneDetailRow
  //   else
  //     DetailRows.NoDetailRows

  val nsStatus: Option[NodAndShuffleStatus] = step.get match
    case x: NodAndShuffleStep => Some(x.nsStatus)
    case _                    => None

  val nsPendingObserveCmd: Option[NodAndShuffleStep.PendingObserveCmd] = step.get match
    case x: NodAndShuffleStep => x.pendingObserveCmd
    case _                    => None

  def isStopping: Boolean =
    tabOperations.stopRequested === StopOperation.StopInFlight

object StepProgressCell:
  private type Props = StepProgressCell

  private given ControlButtonResolver[Props] =
    ControlButtonResolver.build(p => (p.clientStatus, p.sequenceState, p.step.get))

  private def stepControlButtons(props: Props): TagMod =
    StepControlButtons(
      props.obsId,
      props.instrument,
      props.sequenceState,
      props.step.get.id,
      props.step.get.isObservePaused,
      props.step.get.isMultiLevel,
      props.tabOperations
    ).when(props.controlButtonsActive)

  // private def step.getSystemsStatus(step.get: Step): VdomElement =
  //   <.div(
  //     ObserveStyles.configuringRow,
  //     <.div(
  //       ObserveStyles.specialStateLabel,
  //       "Configuring"
  //     ),
  //     <.div(
  //       ObserveStyles.subsystems,
  //       Step.configStatus
  //         .getOption(step.get)
  //         .orEmpty
  //         .sortBy(_._1)
  //         .map(Function.tupled(statusLabel))
  //         .toTagMod
  //     )
  //   )

  private def stepObservationStatusAndFile(
    props:  Props,
    fileId: ImageFileId,
    paused: Boolean
  ): VdomElement =
    <.div(
      ObserveStyles.ConfiguringRow,
      if (props.isBias)
        // BiasStatus(
        //   props.obsIdName,
        //   props.step.get.id,
        //   fileId,
        //   stopping = !paused && props.isStopping,
        //   paused
        // )
        EmptyVdom
      else
        props.nsStatus.fold[VdomNode] {
          ObservationProgressBar(
            props.obsId,
            props.step.get.id,
            fileId,
            stopping = !paused && props.isStopping,
            paused
          )
        } { nsStatus =>
          // NodAndShuffleProgressMessage(props.obsIdName,
          //                              props.step.get.id,
          //                              fileId,
          //                              props.isStopping,
          //                              paused,
          //                              nsStatus
          // )
          EmptyVdom
        },
      stepControlButtons(props)
    )

  private val component = ScalaFnComponent[Props](props =>
    (props.sequenceState, props.step.get) match
      case (f, s) if s.status === StepState.Running && s.fileId.isEmpty && f.userStopRequested =>
        // Case pause at the sequence level
        // step.getObservationPausing(props)
        EmptyVdom
      case (_, s) if s.status === StepState.Running && s.fileId.isEmpty                        =>
        // Case configuring, label and status icons
        // step.getSystemsStatus(s)
        EmptyVdom
      case (_, s) if s.isObservePaused && s.fileId.isDefined                                   =>
        // Case for exposure paused, label and control buttons
        stepObservationStatusAndFile(props, s.fileId.orEmpty, paused = true)
      case (_, s) if s.status === StepState.Running && s.fileId.isDefined                      =>
        // Case for a exposure onging, progress bar and control buttons
        stepObservationStatusAndFile(props, s.fileId.orEmpty, paused = false)
      case (_, s) if s.wasSkipped                                                              =>
        <.p("Skipped")
      case (_, _) if props.step.get.skip                                                       =>
        <.p("Skip")
      case (_, s) if s.status === StepState.Completed && s.fileId.isDefined                    =>
        <.p(ObserveStyles.ComponentLabel, s.fileId.map(_.value).orEmpty)
      case (_, s) if props.stepSelected(s.id) && s.canConfigure                                =>
        // step.getSubsystemControl(props)
        EmptyVdom
      case _                                                                                   =>
        <.p(ObserveStyles.ComponentLabel, props.step.get.shortName)
  )
