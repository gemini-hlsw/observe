// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.react.common.*
import lucuma.ui.sequence.StepTypeDisplay
import observe.model.ObserveStage
import observe.model.SequenceState
import observe.model.StepProgress
import observe.model.dhs.ImageFileId
import observe.model.enums.ActionStatus
import observe.model.enums.Resource
import observe.ui.ObserveStyles
import observe.ui.model.ObservationRequests
import observe.ui.model.enums.ClientMode
import observe.ui.model.enums.OperationRequest

case class StepProgressCell(
  clientMode:      ClientMode,
  instrument:      Instrument,
  stepId:          Step.Id,
  stepType:        StepTypeDisplay,
  isFinished:      Boolean,
  stepIndex:       Int,
  obsId:           Observation.Id,
  requests:        ObservationRequests,
  runningStepId:   Option[Step.Id],
  sequenceState:   SequenceState,
  subsystemStatus: Map[Resource | Instrument, ActionStatus],
  progress:        Option[StepProgress],
  selectedStep:    Option[Step.Id],
  isPreview:       Boolean
) extends ReactFnProps(StepProgressCell.component):
  // def stepSelected(i: Step.Id): Boolean =
  //   selectedStep.exists(_ === i) && !isPreview &&
  //     // Will we always require logging in?
  //     ( /*clientStatus.isLogged ||*/ seqOperations.resourceRunNotIdle(i))

  private val isRunning =
    requests.subsystemInFlight(stepId) || runningStepId.contains_(stepId)

  val anyError: Boolean =
    subsystemStatus.exists(_._2 === ActionStatus.Failed)

  val isBias: Boolean = stepType === StepTypeDisplay.Bias

  // def canControlThisStep(selected: Option[Step.Id], hasControls: Boolean): Boolean =
  //   hasControls && selected.exists(_ === step.get.id)

  // def detailRows(selected: Option[StepId], hasControls: Boolean): DetailRows =
  //   if (((isNS || isNSInError) && canControlThisStep(selected, hasControls)) || isNSObserving)
  //     DetailRows.TwoDetailRows
  //   else if (isACRunning || isACInError)
  //     DetailRows.OneDetailRow
  //   else
  //     DetailRows.NoDetailRows

  def isStopping: Boolean =
    requests.stop === OperationRequest.InFlight

object StepProgressCell:
  private type Props = StepProgressCell

  // private given ControlButtonResolver[Props] =
  //   ControlButtonResolver.build(p => (p.clientMode, p.sequenceState, p.isRunning))

  private def stepControlButtons(props: Props): TagMod =
    StepControlButtons(
      props.obsId,
      props.instrument,
      props.sequenceState,
      props.stepId,
      false, // props.step.get.isObservePaused,
      props.progress.exists(_.stage === ObserveStage.ReadingOut),
      false, // props.isNs,
      props.requests
    )        // .when(props.controlButtonsActive)

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

    // if (props.isRunning)
    //   stepObservationStatusAndFile(props, ImageFileId(""), paused = false)
    // else
    //   <.p(ObserveStyles.ComponentLabel, "Pending")

    val fileId = ImageFileId("")
    val paused = false

    <.div(ObserveStyles.ConfiguringRow)(
      stepControlButtons(props),
      SubsystemControls(
        props.obsId,
        props.stepId,
        props.subsystemStatus.map(_._1).toList,
        props.subsystemStatus,
        props.requests.subsystemRun.getOrElse(props.stepId, Map.empty),
        props.clientMode
      ),
      if (props.isBias)
        // BiasStatus(
        //   props.obsIdName,
        //   props.step.get.id,
        //   fileId,
        //   stopping = !paused && props.isStopping,
        //   paused
        // )
        EmptyVdom
      else if (props.isRunning)
        // props.nsStatus.fold[VdomNode] {
        ObservationProgressBar(
          props.obsId,
          props.stepId,
          props.progress,
          fileId,
          stopping = !paused && props.isStopping,
          paused
        )
      else EmptyVdom
      // } { nsStatus =>
      //   // NodAndShuffleProgressMessage(props.obsIdName,
      //   //                              props.step.get.id,
      //   //                              fileId,
      //   //                              props.isStopping,
      //   //                              paused,
      //   //                              nsStatus
      //   // )
      //   EmptyVdom
      // },
    )
  )
