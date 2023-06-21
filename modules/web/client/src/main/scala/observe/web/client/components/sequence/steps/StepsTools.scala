// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.steps

import cats.syntax.all.*
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^._
import react.common.*
import react.semanticui.elements.icon.IconRotated
import observe.model.{Observation, Step, StepId, StepState}
import observe.web.client.components.ObserveStyles
import observe.web.client.icons.*
import observe.web.client.model.ClientStatus
import observe.web.client.reusability.*
import observe.web.client.services.HtmlConstants.iconEmpty

/**
 * Component to display an icon for the state
 */
final case class StepToolsCell(
  clientStatus:      ClientStatus,
  step:              Step,
  rowHeight:         Int,
  secondRowHeight:   Int,
  isPreview:         Boolean,
  nextStepToRun:     Option[StepId],
  obsIdName:         Observation.IdName,
  canSetBreakpoint:  Boolean,
  breakPointEnterCB: StepId => Callback,
  breakPointLeaveCB: StepId => Callback,
  heightChangeCB:    StepId => Callback
) extends ReactProps[StepToolsCell](StepToolsCell.component)

object StepToolsCell {
  type Props = StepToolsCell

  given Reusability[Props] =
    Reusability.caseClassExcept[Props]("heightChangeCB", "breakPointEnterCB", "breakPointLeaveCB")

  protected val component = ScalaComponent
    .builder[Props]("StepToolsCell")
    .stateless
    .render_P { p =>
      <.div(
        ObserveStyles.controlCell,
        StepBreakStopCell(
          p.clientStatus,
          p.step,
          p.rowHeight,
          p.obsIdName,
          p.canSetBreakpoint,
          p.breakPointEnterCB,
          p.breakPointLeaveCB,
          p.heightChangeCB
        ).when(p.clientStatus.isLogged)
          .unless(p.isPreview),
        StepIconCell(
          p.step.status,
          p.step.skip,
          p.nextStepToRun.forall(_ === p.step.id),
          p.rowHeight - p.secondRowHeight
        )
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build
}

/**
 * Component to display an icon for the state
 */
final case class StepIconCell(
  status:    StepState,
  skip:      Boolean,
  nextToRun: Boolean,
  height:    Int
) extends ReactProps[StepIconCell](StepIconCell.component)

object StepIconCell {
  type Props = StepIconCell

  given Reusability[Props] = Reusability.derive[Props]

  private def stepIcon(p: Props): VdomNode =
    p.status match {
      case StepState.Completed => IconCheckmark
      case StepState.Running   => IconCircleNotched.loading(true)
      case StepState.Failed(_) => IconAttention
      case StepState.Skipped   =>
        IconReply.copy(fitted = true, rotated = IconRotated.CounterClockwise)
      case _ if p.skip         =>
        IconReply.copy(fitted = true, rotated = IconRotated.CounterClockwise)
      case _ if p.nextToRun    => IconChevronRight
      case _                   => iconEmpty
    }

  private def stepStyle(p: Props): Css =
    p.status match {
      case StepState.Running   => ObserveStyles.runningIconCell
      case StepState.Skipped   => ObserveStyles.skippedIconCell
      case StepState.Completed => ObserveStyles.completedIconCell
      case StepState.Failed(_) => ObserveStyles.errorCell
      case _ if p.skip         => ObserveStyles.skippedIconCell
      case _                   => ObserveStyles.iconCell
    }

  protected val component = ScalaComponent
    .builder[Props]("StepIconCell")
    .stateless
    .render_P(p =>
      <.div(
        ^.height := p.height.px,
        stepStyle(p),
        stepIcon(p)
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build
}
