// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import observe.model.ClientStatus
import observe.model.ImageFileId
import observe.model.NodAndShuffleStatus
import observe.model.RunningStep
import observe.model.enums.ExecutionStepType
import observe.model.enums.SequenceState
import observe.ui.ObserveStyles
import observe.ui.model.StopOperation
import observe.ui.model.TabOperations
import react.common.*

case class StepProgressCell(
  clientStatus:  ClientStatus,
  instrument:    Instrument,
  stepId:        Step.Id,
  stepType:      ExecutionStepType,
  isFinished:    Boolean,
  stepIndex:     Int,
  obsId:         Observation.Id,
  tabOperations: TabOperations,
  sequenceState: SequenceState,
  runningStep:   Option[RunningStep],
  nsStatus:      Option[NodAndShuffleStatus],
  selectedStep:  Option[Step.Id],
  isPreview:     Boolean
) extends ReactFnProps(StepProgressCell.component):
  def stepSelected(i: Step.Id): Boolean =
    selectedStep.exists(_ === i) && !isPreview &&
      (clientStatus.isLogged || tabOperations.resourceRunNotIdle(i))

  // val isAC: Boolean =
  //   step.alignAndCalib(instrument).isDefined

  val isNs: Boolean = nsStatus.isDefined

  private val isRunning =
    tabOperations.resourceInFlight(stepId) || runningStep.flatMap(_.id).contains_(stepId)

  // val isACRunning: Boolean =
  //   isAC && isRunning

  val isNsRunning: Boolean =
    isNs && isRunning

  val isNsObserving: Boolean =
    isNs && isRunning

  val anyError: Boolean =
    tabOperations.resourceInError(stepId) // || step.get.hasError

  // val isACInError: Boolean =
  //   isAC && anyError

  val isNsInError: Boolean =
    isNs && anyError

  val isBias: Boolean = stepType === ExecutionStepType.Bias

  // def canControlThisStep(selected: Option[Step.Id], hasControls: Boolean): Boolean =
  //   hasControls && selected.exists(_ === step.get.id)

  // def detailRows(selected: Option[StepId], hasControls: Boolean): DetailRows =
  //   if (((isNS || isNSInError) && canControlThisStep(selected, hasControls)) || isNSObserving)
  //     DetailRows.TwoDetailRows
  //   else if (isACRunning || isACInError)
  //     DetailRows.OneDetailRow
  //   else
  //     DetailRows.NoDetailRows

  // val nsStatus: Option[NodAndShuffleStatus] = step.get match
  //   case x: NodAndShuffleStep => Some(x.nsStatus)
  //   case _                    => None

  // val nsPendingObserveCmd: Option[NodAndShuffleStep.PendingObserveCmd] = step.get match
  //   case x: NodAndShuffleStep => x.pendingObserveCmd
  //   case _                    => None

  def isStopping: Boolean =
    tabOperations.stopRequested === StopOperation.StopInFlight

object StepProgressCell:
  private type Props = StepProgressCell

  private given ControlButtonResolver[Props] =
    ControlButtonResolver.build(p => (p.clientStatus, p.sequenceState, p.isRunning))

  private def stepControlButtons(props: Props): TagMod =
    StepControlButtons(
      props.obsId,
      props.instrument,
      props.sequenceState,
      props.stepId,
      false,      // props.step.get.isObservePaused,
      props.isNs, // props.step.get.isMultiLevel,
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
            props.stepId,
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
    // (props.sequenceState, props.step.get) match
    //       case (f, s) if s.status === StepState.Running && s.fileId.isEmpty && f.userStopRequested =>
    // Case pause at the sequence level
    // step.getObservationPausing(props)
    // EmptyVdom
    //       case (_, s) if s.status === StepState.Running && s.fileId.isEmpty                        =>
    // Case configuring, label and status icons
    // step.getSystemsStatus(s)
    // EmptyVdom
    // case (_, s) if s.isObservePaused && s.fileId.isDefined                =>
    // Case for exposure paused, label and control buttons
    // stepObservationStatusAndFile(props, s.fileId.orEmpty, paused = true)
    // case (_, s) if s.status === StepState.Running && s.fileId.isDefined   =>
    // Case for a exposure onging, progress bar and control buttons
    // stepObservationStatusAndFile(props, s.fileId.orEmpty, paused = false)
    // case (_, s) if s.wasSkipped                                                              =>
    //   <.p("Skipped")
    // case (_, _) if props.step.get.skip                                                       =>
    //   <.p("Skip")
    // case (_, s) if s.status === StepState.Completed && s.fileId.isDefined =>
    //   <.p(ObserveStyles.ComponentLabel, s.fileId.map(_.value).orEmpty)
    // case (_, s) if props.stepSelected(s.id) && s.canConfigure             =>
    //   // step.getSubsystemControl(props)
    //   EmptyVdom
    // case _                                                                =>
    // <.p(ObserveStyles.ComponentLabel, props.step.get.shortName)

    if (props.isRunning)
      stepObservationStatusAndFile(props, ImageFileId(""), paused = false)
    else
      <.p(ObserveStyles.ComponentLabel, "Pending")
  )
