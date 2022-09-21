// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import react.common.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import observe.model.ExecutionStep
import observe.model.ClientStatus
import lucuma.core.model.Observation
import lucuma.core.enums.Instrument
import observe.model.enums.SequenceState
import lucuma.core.model.sequence.Step
import observe.ui.model.TabOperations
import cats.syntax.all.*
import observe.model.enums.StepState
import alleycats.Empty
import observe.model.ImageFileId
import observe.ui.ObserveStyles
import observe.ui.model.extensions.*
import observe.model.enums.ExecutionStepType
import observe.model.NodAndShuffleStatus
import observe.model.NodAndShuffleStep
import observe.ui.model.StopOperation
import lucuma.core.syntax.display.*

case class StepProgressCell(
  clientStatus:  ClientStatus,
  step:          ExecutionStep,
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
    step.alignAndCalib(instrument).isDefined

  val isNS: Boolean =
    step.nodAndShuffle(instrument).isDefined

  private val isRunning =
    tabOperations.resourceInFlight(step.id) || step.isRunning

  val isACRunning: Boolean =
    isAC && isRunning

  val isNSRunning: Boolean =
    isNS && isRunning

  val isNSObserving: Boolean =
    isNS && step.isRunning

  val anyError: Boolean =
    tabOperations.resourceInError(step.id) || step.hasError

  val isACInError: Boolean =
    isAC && anyError

  val isNSInError: Boolean =
    isNS && anyError

  val isBias: Boolean =
    step.stepType(instrument).exists(_ === ExecutionStepType.Bias)

  def canControlThisStep(selected: Option[Step.Id], hasControls: Boolean): Boolean =
    hasControls && selected.exists(_ === step.id)

  // def detailRows(selected: Option[StepId], hasControls: Boolean): DetailRows =
  //   if (((isNS || isNSInError) && canControlThisStep(selected, hasControls)) || isNSObserving)
  //     DetailRows.TwoDetailRows
  //   else if (isACRunning || isACInError)
  //     DetailRows.OneDetailRow
  //   else
  //     DetailRows.NoDetailRows

  val nsStatus: Option[NodAndShuffleStatus] = step match
    case x: NodAndShuffleStep => Some(x.nsStatus)
    case _                    => None

  val nsPendingObserveCmd: Option[NodAndShuffleStep.PendingObserveCmd] = step match
    case x: NodAndShuffleStep => x.pendingObserveCmd
    case _                    => None

  def isStopping: Boolean =
    tabOperations.stopRequested === StopOperation.StopInFlight

object StepProgressCell:
  private type Props = StepProgressCell

  private given ControlButtonResolver[Props] =
    ControlButtonResolver.build(p => (p.clientStatus, p.sequenceState, p.step))

  private def stepControlButtons(props: Props): TagMod =
    StepControlButtons(
      props.obsId,
      props.instrument,
      props.sequenceState,
      props.step.id,
      props.step.isObservePaused,
      props.step.isMultiLevel,
      props.tabOperations
    ).when(props.controlButtonsActive)

  // private def stepSystemsStatus(step: Step): VdomElement =
  //   <.div(
  //     ObserveStyles.configuringRow,
  //     <.div(
  //       ObserveStyles.specialStateLabel,
  //       "Configuring"
  //     ),
  //     <.div(
  //       ObserveStyles.subsystems,
  //       Step.configStatus
  //         .getOption(step)
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
        //   props.step.id,
        //   fileId,
        //   stopping = !paused && props.isStopping,
        //   paused
        // )
        EmptyVdom
      else
        props.nsStatus.fold[VdomNode] {
          ObservationProgressBar(
            props.obsId,
            props.step.id,
            fileId,
            stopping = !paused && props.isStopping,
            paused
          )
        } { nsStatus =>
          // NodAndShuffleProgressMessage(props.obsIdName,
          //                              props.step.id,
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
    (props.sequenceState, props.step) match
      case (f, s) if s.status === StepState.Running && s.fileId.isEmpty && f.userStopRequested =>
        // Case pause at the sequence level
        // stepObservationPausing(props)
        EmptyVdom
      case (_, s) if s.status === StepState.Running && s.fileId.isEmpty                        =>
        // Case configuring, label and status icons
        // stepSystemsStatus(s)
        EmptyVdom
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
        <.p(ObserveStyles.ComponentLabel, s.fileId.map(_.value).orEmpty)
      case (_, s) if props.stepSelected(s.id) && s.canConfigure                                =>
        // stepSubsystemControl(props)
        EmptyVdom
      case _                                                                                   =>
        <.p(ObserveStyles.ComponentLabel, props.step.shortName)
  )
