// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.steps

import cats.syntax.all._
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.ReactMouseEvent
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import react.semanticui.colors._
import react.semanticui.elements.button.Button
import react.semanticui.modules.popup.Popup
import observe.model.{ Observation, StepId }
import observe.web.client.actions.RequestRunFrom
import observe.web.client.actions.RunOptions
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.components.ObserveStyles
import observe.web.client.icons._
import observe.web.client.model.StartFromOperation
import observe.web.client.reusability._

/**
 * Contains the control to start a step from an arbitrary point
 */
final case class RunFromStep(
  idName:           Observation.IdName,
  stepId:           StepId,
  stepIdx:          Int,
  resourceInFlight: Boolean,
  runFrom:          StartFromOperation
) extends ReactProps[RunFromStep](RunFromStep.component)

object RunFromStep {
  type Props = RunFromStep

  implicit val propsReuse: Reusability[Props] = Reusability.derive[Props]

  def requestRunFrom(
    idName:  Observation.IdName,
    stepId:  StepId,
    stepIdx: Int
  ): (ReactMouseEvent, Button.ButtonProps) => Callback =
    (e: ReactMouseEvent, _: Button.ButtonProps) =>
      ObserveCircuit
        .dispatchCB(RequestRunFrom(idName, stepId, stepIdx, RunOptions.Normal))
        .unless_(e.altKey || e.button === StepsTable.MiddleButton)

  protected val component = ScalaComponent
    .builder[Props]("RunFromStep")
    .render_P { p =>
      <.div(
        ObserveStyles.runFrom,
        ObserveStyles.notInMobile,
        Popup(
          trigger = Button(
            icon = true,
            color = Blue,
            onClickE = requestRunFrom(p.idName, p.stepId, p.stepIdx),
            disabled = p.resourceInFlight || p.runFrom === StartFromOperation.StartFromInFlight
          )(IconPlay)
        )(s"Run from step ${p.stepIdx + 1}")
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build
}
