// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
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
case class StepSkipCell(clientStatus: ClientStatus, step: View[ExecutionStep])
    extends ReactFnProps(StepSkipCell.component)

object StepSkipCell:
  private type Props = StepSkipCell

  // Request a to flip the skip
  private def flipSkipped(step: View[ExecutionStep]): Callback =
    step.zoom(ExecutionStep.skip).mod(!_) >> Callback.log("TODO: Flip skipped")

  private val component = ScalaFnComponent[Props](props =>
    val canSetSkipMark = props.clientStatus.canOperate && props.step.get.canSetSkipmark

    <.div(
      <.div(
        ObserveStyles.SkipHandle,
        Icons.SquarePlus
          .withFixedWidth()(^.onClick --> flipSkipped(props.step))
          .when(props.step.get.skip),
        Icons.CircleMinus
          .withClass(ObserveStyles.SkipIconSet)
          .withFixedWidth()(^.onClick --> flipSkipped(props.step))
          .unless(props.step.get.skip)
      )
        .when(canSetSkipMark)
    )
  )
