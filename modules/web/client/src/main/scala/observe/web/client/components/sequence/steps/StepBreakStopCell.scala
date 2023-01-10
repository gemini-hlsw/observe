// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.steps

import japgolly.scalajs.react.{CtorType, Reusability, _}
import japgolly.scalajs.react.component.Scala.Component
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import react.semanticui.colors._
import observe.model.{Observation, Step, StepId}
import observe.web.client.actions.FlipBreakpointStep
import observe.web.client.actions.FlipSkipStep
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.components.ObserveStyles
import observe.web.client.icons._
import observe.web.client.model.ClientStatus
import observe.web.client.reusability._

/**
 * Component to display an icon for the state
 */
final case class StepBreakStopCell(
  clientStatus:      ClientStatus,
  step:              Step,
  rowHeight:         Int,
  obsIdName:         Observation.IdName,
  canSetBreakpoint:  Boolean,
  breakPointEnterCB: StepId => Callback,
  breakPointLeaveCB: StepId => Callback,
  heightChangeCB:    StepId => Callback
) extends ReactProps[StepBreakStopCell](StepBreakStopCell.component)

object StepBreakStopCell {
  type Props = StepBreakStopCell

  implicit val propsReuse: Reusability[Props] =
    Reusability.caseClassExcept[Props]("heightChangeCB", "breakPointEnterCB", "breakPointLeaveCB")

  // Request a to flip the breakpoint
  def flipBreakpoint(p: Props)(e: ReactEvent): Callback =
    e.preventDefaultCB *>
      e.stopPropagationCB *>
      Callback.when(p.clientStatus.canOperate)(
        ObserveCircuit.dispatchCB(FlipBreakpointStep(p.obsIdName, p.step)) *>
          p.heightChangeCB(p.step.id)
      )

  // Request a to flip the skip
  def flipSkipped(p: Props)(e: ReactEvent): Callback =
    e.preventDefaultCB *>
      e.stopPropagationCB *>
      Callback.when(p.clientStatus.canOperate)(
        ObserveCircuit.dispatchCB(FlipSkipStep(p.obsIdName, p.step))
      )

  protected val component: Component[Props, Unit, Unit, CtorType.Props] = ScalaComponent
    .builder[Props]("StepBreakStopCell")
    .stateless
    .render_P { p =>
      val canSetBreakpoint = p.clientStatus.canOperate && p.canSetBreakpoint
      val canSetSkipMark   = p.clientStatus.canOperate && p.step.canSetSkipmark
      <.div(
        ObserveStyles.gutterCell,
        ^.height := p.rowHeight.px,
        <.div(
          ObserveStyles.breakPointHandleOff.when(p.step.breakpoint),
          ObserveStyles.breakPointHandleOn.unless(p.step.breakpoint),
          ^.onClick ==> flipBreakpoint(p),
          IconRemove
            .copy(fitted = true, clazz = ObserveStyles.breakPointOffIcon)(
              ^.onMouseEnter --> p.breakPointEnterCB(p.step.id),
              ^.onMouseLeave --> p.breakPointLeaveCB(p.step.id)
            )
            .when(p.step.breakpoint),
          IconCaretDown
            .copy(fitted = true, clazz = ObserveStyles.breakPointOnIcon)(
              ^.onMouseEnter --> p.breakPointEnterCB(p.step.id),
              ^.onMouseLeave --> p.breakPointLeaveCB(p.step.id)
            )
            .unless(p.step.breakpoint)
        ).when(canSetBreakpoint),
        <.div(
          ObserveStyles.skipHandle,
          ^.top := (p.rowHeight / 2 - ObserveStyles.skipHandleHeight + 2).px,
          IconPlusSquareOutline
            .copy(link = true)(^.onClick ==> flipSkipped(p))
            .when(p.step.skip),
          IconMinusCircle
            .copy(link = true, color = Orange)(^.onClick ==> flipSkipped(p))
            .unless(p.step.skip)
        ).when(canSetSkipMark)
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build
}
