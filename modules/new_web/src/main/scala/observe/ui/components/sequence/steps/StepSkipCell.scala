// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.sequence.Step
import react.common.*
import observe.ui.ObserveStyles
import observe.ui.Icons
import observe.model.enums.StepState
import observe.model.ClientStatus
import observe.model.ExecutionStep
import lucuma.core.model.Observation
import react.fa.FontAwesomeIcon
import react.fa.Rotation
import crystal.react.View

/**
 * Component to display an icon for the state
 */
case class StepSkipCell(
  clientStatus: ClientStatus,
  step:         View[ExecutionStep],
  obsId:        Observation.Id,
  obsName:      String
  // canSetBreakpoint:  Boolean,
  // breakPointEnterCB: Step.Id => Callback,
  // breakPointLeaveCB: Step.Id => Callback,
  // heightChangeCB:    Step.Id => Callback
) extends ReactFnProps(StepSkipCell.component)

object StepSkipCell:
  private type Props = StepSkipCell

  // Request a to flip the breakpoint
  // private def flipBreakpoint(p: Props)(e: ReactEvent): Callback =
  // e.preventDefaultCB >>
  // e.stopPropagationCB // >>
  // Callback.when(p.clientStatus.canOperate)(
  //   ObserveCircuit.dispatchCB(FlipBreakpointStep(p.obsIdName, p.step)) *>
  //     p.heightChangeCB(p.step.id)
  // )

  // Request a to flip the skip
  private def flipSkipped(p: Props)(e: ReactEvent): Callback =
    e.preventDefaultCB >>
      e.stopPropagationCB // >>
    // Callback.when(p.clientStatus.canOperate)(
    //   ObserveCircuit.dispatchCB(FlipSkipStep(p.obsIdName, p.step))
    // )

  private val component = ScalaFnComponent[Props](props =>
    // val canSetBreakpoint = props.clientStatus.canOperate && props.canSetBreakpoint
    val canSetSkipMark = props.clientStatus.canOperate && props.step.get.canSetSkipmark

    <.div(
      // ObserveStyles.skipHandle,
      // ^.top := (props.rowHeight / 2 - ObserveStyles.skipHandleHeight + 2).px,
      // Icons.PlusSquareOutline
      //   .copy(link = true)(^.onClick ==> flipSkipped(props))
      //   .when(p.step.skip),
      // Icons.MinusCircle
      //   .copy(link = true, color = Orange)(^.onClick ==> flipSkipped(props))
      //   .unless(p.step.skip)
    ) // .when(canSetSkipMark)
  )
