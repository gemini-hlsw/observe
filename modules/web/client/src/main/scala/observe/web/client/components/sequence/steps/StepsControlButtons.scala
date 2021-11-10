// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.steps

import cats.syntax.all._
import diode.react.ReactConnectProxy
import japgolly.scalajs.react.{ Callback, CtorType, Reusability, ScalaComponent }
import japgolly.scalajs.react.component.Scala.Component
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import react.semanticui.colors._
import react.semanticui.elements.button.Button
import react.semanticui.elements.icon._
import react.semanticui.modules.popup.Popup
import react.semanticui.modules.popup.PopupPosition
import observe.model.Observation
import observe.model._
import observe.model.enum._
import observe.model.operations.Operations._
import observe.model.operations._
import observe.web.client.actions._
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.components.ObserveStyles
import observe.web.client.icons._
import observe.web.client.model.TabOperations
import observe.web.client.reusability._

/**
 * Contains a set of control buttons like stop/abort
 */
final case class ControlButtons(
  obsId:               Observation.Id,
  displayName:         String,
  operations:          List[Operations[_]],
  sequenceState:       SequenceState,
  stepId:              StepId,
  isObservePaused:     Boolean,
  tabOperations:       TabOperations,
  nsPendingObserveCmd: Option[NodAndShuffleStep.PendingObserveCmd] = None
) extends ReactProps[ControlButtons](ControlButtons.component) {

  val requestInFlight: Boolean = tabOperations.stepRequestInFlight

  protected[steps] val connect: ReactConnectProxy[Option[Progress]] =
    ObserveCircuit.connect(ObserveCircuit.obsProgressReader[Progress](obsIdName.id, stepId))
}

object ControlButtons {
  type Props = ControlButtons

  implicit val operationsReuse: Reusability[Operations[_]] = Reusability.derive[Operations[_]]
  implicit val propsReuse: Reusability[Props]              = Reusability.derive[Props]

  private def requestStop(obsIdName: Observation.Id, name: Observer, stepId: StepId): Callback =
    ObserveCircuit.dispatchCB(RequestStop(obsId, name, stepId))

  private def requestGracefulStop(obsId: Observation.Id, name: Observer, stepId: StepId): Callback =
    ObserveCircuit.dispatchCB(RequestGracefulStop(obsId, name, stepId))

  private def requestAbort(obsIdName: Observation.Id, stepId: StepId): Callback =
    ObserveCircuit.dispatchCB(RequestAbort(obsIdName, stepId))

  private def requestObsPause(obsId: Observation.Id, stepId: StepId): Callback =
    ObserveCircuit.dispatchCB(RequestObsPause(obsId, stepId))

  private def requestGracefulObsPause(
    obsId:  Observation.Id,
    name:   Observer,
    stepId: Int
  ): Callback =
    SeqexecCircuit.dispatchCB(RequestGracefulObsPause(obsId, name, stepId))

  private def requestObsResume(obsId: Observation.Id, stepId: StepId): Callback =
    ObserveCircuit.dispatchCB(RequestObsResume(obsId, stepId))

  private def requestedIcon(icon: Icon): IconGroup =
    IconGroup(
      icon(^.key                                                   := "main"),
      IconCircleNotched.copy(loading = true, color = Yellow)(^.key := "requested")
    )

  protected val component = ScalaComponent
    .builder[Props]
    .render_P { p =>
      val pauseGracefullyIcon: VdomNode =
        p.nsPendingObserveCmd
          .collect { case NodAndShuffleStep.PauseGracefully =>
            requestedIcon(IconPause): VdomNode
          }
          .getOrElse(IconPause)

      val stopGracefullyIcon: VdomNode =
        p.nsPendingObserveCmd
          .collect { case NodAndShuffleStep.StopGracefully =>
            requestedIcon(IconStop): VdomNode
          }
          .getOrElse(IconStop)

      p.connect { proxy =>
        val isReadingOut = proxy().exists(_.stage === ObserveStage.ReadingOut)
        val observer     = Observer(p.displayName)

        <.div(
          ^.cls := "ui icon buttons",
          ObserveStyles.notInMobile,
          p.operations.map {
            case PauseObservation            =>
              Popup(
                position = PopupPosition.TopRight,
                trigger = Button(
                  icon = true,
                  color = Teal,
                  onClick = requestObsPause(p.obsIdName, observer, p.stepId),
                  disabled = p.requestInFlight || p.isObservePaused || isReadingOut
                )(IconPause)
              )("Pause the current exposure")
            case StopObservation             =>
              Popup(
                position = PopupPosition.TopRight,
                trigger = Button(
                  icon = true,
                  color = Orange,
                  onClick = requestStop(p.obsIdName, observer, p.stepId),
                  disabled = p.requestInFlight || isReadingOut
                )(IconStop)
              )("Stop the current exposure early")
            case AbortObservation            =>
              Popup(
                position = PopupPosition.TopRight,
                trigger = Button(
                  icon = true,
                  color = Red,
                  onClick = requestAbort(p.obsIdName, observer, p.stepId),
                  disabled = p.requestInFlight || isReadingOut
                )(IconTrash)
              )("Abort the current exposure")
            case ResumeObservation           =>
              Popup(
                position = PopupPosition.TopRight,
                trigger = Button(
                  icon = true,
                  color = Blue,
                  onClick = requestObsResume(p.obsIdName.id, p.stepId),
                  disabled = p.requestInFlight || !p.isObservePaused || isReadingOut
                )(IconPlay)
              )("Resume the current exposure")
            // N&S operations
            case PauseImmediatelyObservation =>
              Popup(
                position = PopupPosition.TopRight,
                trigger = Button(
                  icon = true,
                  color = Teal,
                  basic = true,
                  onClick = requestObsPause(p.obsIdName, observer, p.stepId),
                  disabled = p.requestInFlight || p.isObservePaused || isReadingOut
                )(IconPause)
              )("Pause the current exposure immediately")
            case PauseGracefullyObservation  =>
              Popup(
                position = PopupPosition.TopRight,
                trigger = Button(
                  icon = true,
                  color = Teal,
                  onClick = requestGracefulObsPause(p.obsIdName, observer, p.stepId),
                  disabled =
                    p.requestInFlight || p.isObservePaused || p.nsPendingObserveCmd.isDefined || isReadingOut
                )(pauseGracefullyIcon)
              )("Pause the current exposure at the end of the cycle")
            case StopImmediatelyObservation  =>
              Popup(
                position = PopupPosition.TopRight,
                trigger = Button(
                  icon = true,
                  color = Orange,
                  basic = true,
                  onClick = requestStop(p.obsIdName, Observer(p.displayName), p.stepId),
                  disabled = p.requestInFlight || isReadingOut
                )(IconStop)
              )("Stop the current exposure immediately")
            case StopGracefullyObservation   =>
              Popup(
                position = PopupPosition.TopRight,
                trigger = Button(
                  icon = true,
                  color = Orange,
                  onClick = requestGracefulStop(p.obsIdName, Observer(p.displayName), p.stepId),
                  disabled =
                    p.requestInFlight || p.isObservePaused || p.nsPendingObserveCmd.isDefined || isReadingOut
                )(stopGracefullyIcon)
              )("Stop the current exposure at the end of the cycle")
          }.toTagMod
        )
      }
    }
    .configure(Reusability.shouldComponentUpdate)
    .build
}

/**
 * Contains the control buttons like stop/abort at the row level
 */
final case class StepsControlButtons(
  obsId:           Observation.IdName,
  displayName:     String,
  instrument:      Instrument,
  sequenceState:   SequenceState,
  stepId:          StepId,
  isObservePaused: Boolean,
  isMultiLevel:    Boolean,
  tabOperations:   TabOperations
) extends ReactProps[StepsControlButtons](StepsControlButtons.component) {

  val requestInFlight: Boolean = tabOperations.stepRequestInFlight
}

object StepsControlButtons {
  type Props = StepsControlButtons

  implicit val propsReuse: Reusability[Props] = Reusability.derive[Props]

  protected val component: Component[Props, Unit, Unit, CtorType.Props] = ScalaComponent
    .builder[Props]("StepsControlButtons")
    .render_P { p =>
      ControlButtons(
        p.obsIdName,
        p.displayName,
        p.instrument.operations[OperationLevel.Observation](p.isObservePaused, p.isMultiLevel),
        p.sequenceState,
        p.stepId,
        p.isObservePaused,
        p.tabOperations
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build
}
