// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import react.common.*
import lucuma.core.model.Observation
import observe.model.enums.SequenceState
import observe.ui.model.TabOperations
import lucuma.core.model.sequence.Step
import observe.model.NodAndShuffleStep
import observe.model.operations.*
import react.fa.FontAwesomeIcon
import observe.ui.ObserveStyles
import observe.model.operations.Operations.*
import observe.ui.Icons
import reactST.primereact.components.*
import reactST.primereact.tooltipoptionsMod.TooltipOptions
import reactST.primereact.tooltipoptionsMod.TooltipPositionType

/**
 * Contains a set of control buttons like stop/abort
 */
case class ControlButtons(
  obsId:               Observation.Id,
  operations:          List[Operations],
  sequenceState:       SequenceState,
  stepId:              Step.Id,
  isObservePaused:     Boolean,
  tabOperations:       TabOperations,
  nsPendingObserveCmd: Option[NodAndShuffleStep.PendingObserveCmd] = None
) extends ReactFnProps(ControlButtons.component):

  val requestInFlight: Boolean = tabOperations.stepRequestInFlight

  // TODO Substitute for a stream hook - Progress should be a Topic somewhere
  // protected[steps] val connect: ReactConnectProxy[Option[Progress]] =
  //   ObserveCircuit.connect(ObserveCircuit.obsProgressReader[Progress](obsId, stepId))

object ControlButtons:
  private type Props = ControlButtons

  private val component = ScalaFnComponent[Props] { props =>
    def requestedIcon(icon: FontAwesomeIcon): VdomNode =
      icon
      // IconGroup(
      //   icon(^.key                                                   := "main"),
      //   IconCircleNotched.copy(loading = true, color = Yellow)(^.key := "requested")
      // )

    val pauseGracefullyIcon: VdomNode =
      props.nsPendingObserveCmd match
        case Some(NodAndShuffleStep.PendingObserveCmd.PauseGracefully) => requestedIcon(Icons.Pause)
        case _                                                         => Icons.Pause

    val stopGracefullyIcon: VdomNode =
      props.nsPendingObserveCmd match
        case Some(NodAndShuffleStep.PendingObserveCmd.StopGracefully) => requestedIcon(Icons.Stop)
        case _                                                        => Icons.Stop

        // p.connect { proxy =>
    val isReadingOut                 = false // proxy().exists(_.stage === ObserveStage.ReadingOut)

    <.div(ObserveStyles.ControlButtonStrip, ^.cls := "p-inputgroup")(
      // ObserveStyles.notInMobile,
      props.operations
        .map[VdomNode] {
          case PauseObservation =>
            // Tooltip(
            Button(ObserveStyles.PauseButton)
              .iconFA(Icons.Pause)
              .disabled(props.requestInFlight || props.isObservePaused || isReadingOut)
              .tooltip("Pause the current exposure")
              .tooltipOptions(TooltipOptions().setPosition(TooltipPositionType.top))
          // onClick = requestObsPause(p.obsId, p.stepId),
          case StopObservation  =>
            Button(ObserveStyles.StopButton)
              .iconFA(Icons.Stop)
              .disabled(props.requestInFlight || isReadingOut)
              .tooltip("Stop the current exposure early")
              .tooltipOptions(TooltipOptions().setPosition(TooltipPositionType.top))
          // onClick = requestStop(p.obsId, p.stepId),
          case AbortObservation =>
            Button(ObserveStyles.AbortButton)
              .iconFA(Icons.Trash)
              .disabled(props.requestInFlight || isReadingOut)
              .tooltip("Abort the current exposure")
              .tooltipOptions(TooltipOptions().setPosition(TooltipPositionType.top))
          // onClick = requestAbort(p.obsId, p.stepId),
          // case ResumeObservation           =>
          //   Popup(
          //     position = PopupPosition.TopRight,
          //     trigger = Button(
          //       icon = true,
          //       color = Blue,
          //       onClick = requestObsResume(p.obsId, p.stepId),
          //       disabled = p.requestInFlight || !p.isObservePaused || isReadingOut
          //     )(IconPlay)
          //   )("Resume the current exposure")
          // // N&S operations
          // case PauseImmediatelyObservation =>
          //   Popup(
          //     position = PopupPosition.TopRight,
          //     trigger = Button(
          //       icon = true,
          //       color = Teal,
          //       basic = true,
          //       onClick = requestObsPause(p.obsId, p.stepId),
          //       disabled = p.requestInFlight || p.isObservePaused || isReadingOut
          //     )(IconPause)
          //   )("Pause the current exposure immediately")
          // case PauseGracefullyObservation  =>
          //   Popup(
          //     position = PopupPosition.TopRight,
          //     trigger = Button(
          //       icon = true,
          //       color = Teal,
          //       onClick = requestGracefulObsPause(p.obsId, p.stepId),
          //       disabled =
          //         p.requestInFlight || p.isObservePaused || p.nsPendingObserveCmd.isDefined || isReadingOut
          //     )(pauseGracefullyIcon)
          //   )("Pause the current exposure at the end of the cycle")
          // case StopImmediatelyObservation  =>
          //   Popup(
          //     position = PopupPosition.TopRight,
          //     trigger = Button(
          //       icon = true,
          //       color = Orange,
          //       basic = true,
          //       onClick = requestStop(p.obsId, p.stepId),
          //       disabled = p.requestInFlight || isReadingOut
          //     )(IconStop)
          //   )("Stop the current exposure immediately")
          // case StopGracefullyObservation   =>
          //   Popup(
          //     position = PopupPosition.TopRight,
          //     trigger = Button(
          //       icon = true,
          //       color = Orange,
          //       onClick = requestGracefulStop(p.obsId, p.stepId),
          //       disabled =
          //         p.requestInFlight || p.isObservePaused || p.nsPendingObserveCmd.isDefined || isReadingOut
          //     )(stopGracefullyIcon)
          //   )("Stop the current exposure at the end of the cycle")
          case _                => <.div
        }
        .toTagMod
    )
  }
