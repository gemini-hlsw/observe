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

case class StepToolsCell(
  clientStatus:      ClientStatus,
  step:              ExecutionStep,
  rowHeight:         Int,
  secondRowHeight:   Int,
  isPreview:         Boolean,
  nextStepToRun:     Option[Step.Id],
  obsId:             Observation.Id,
  obsName:           String,
  canSetBreakpoint:  Boolean,
  breakPointEnterCB: Step.Id => Callback,
  breakPointLeaveCB: Step.Id => Callback,
  heightChangeCB:    Step.Id => Callback
) extends ReactFnProps(StepToolsCell.component)

object StepToolsCell:
  private type Props = StepToolsCell

  private val component = ScalaFnComponent[Props](props =>
    <.div(ObserveStyles.ControlCell)(
      <.div(ObserveStyles.GutterCell),
      // StepBreakStopCell(
      //   p.clientStatus,
      //   p.step,
      //   p.rowHeight,
      //   p.obsIdName,
      //   p.canSetBreakpoint,
      //   p.breakPointEnterCB,
      //   p.breakPointLeaveCB,
      //   p.heightChangeCB
      // ).when(p.clientStatus.isLogged)
      //   .unless(p.isPreview),
      StepIconCell(
        props.step.status,
        props.step.skip,
        props.nextStepToRun.forall(_ === props.step.id),
        props.rowHeight - props.secondRowHeight
      )
    )
  )

  /**
   * Component to display an icon for the state
   */
  case class StepIconCell(
    status:    StepState,
    skip:      Boolean,
    nextToRun: Boolean,
    height:    Int
  ) extends ReactFnProps(StepIconCell.component)

  object StepIconCell:
    private type Props = StepIconCell

    private def stepIcon(props: Props): Option[FontAwesomeIcon] =
      props.status match
        case StepState.Completed  => Icons.Check.some
        case StepState.Running    => Icons.CircleNotch.withFixedWidth().withSpin(true).some
        case StepState.Failed(_)  => Icons.CircleExclamation.withFixedWidth().some
        case StepState.Skipped    => Icons.Reply.withFixedWidth().withRotation(Rotation.Rotate270).some
        case _ if props.skip      => Icons.Reply.withFixedWidth().withRotation(Rotation.Rotate270).some
        case _ if props.nextToRun => Icons.ChevronRight.withFixedWidth().some
        case _                    => none

    private def stepStyle(props: Props): Css =
      props.status match
        case StepState.Skipped => ObserveStyles.SkippedIconCell
        case _                 => Css.Empty

    private val component = ScalaFnComponent[Props](props =>
      <.div(
        // ^.height := props.height.px,
        ObserveStyles.IconCell |+| stepStyle(props),
        stepIcon(props)
      )
    )
