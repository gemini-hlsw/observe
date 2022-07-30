// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.steps

import scala.collection.immutable.SortedMap
import scala.scalajs.js
import cats.syntax.all._
import cats.Order._
import japgolly.scalajs.react._
import japgolly.scalajs.react.Reusability._
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import react.semanticui.SemanticColor
import react.semanticui.colors._
import react.semanticui.elements.button.Button
import react.semanticui.elements.button.LabelPosition
import react.semanticui.elements.icon.Icon
import react.semanticui.modules.popup.Popup
import react.semanticui.sizes._
import observe.model.Observation
import observe.model.StepId
import observe.model.enum._
import observe.web.client.actions.RequestResourceRun
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.components.ObserveStyles
import observe.web.client.icons._
import observe.web.client.model.ResourceRunOperation
import observe.web.client.reusability._

/**
 * Contains the control buttons for each subsystem
 */
final case class SubsystemControlCell(
  id:             Observation.Id,
  stepId:         StepId,
  resources:      List[Resource],
  resourcesCalls: SortedMap[Resource, ResourceRunOperation],
  canOperate:     Boolean
) extends ReactProps[SubsystemControlCell](SubsystemControlCell.component)

object SubsystemControlCell {
  type Props = SubsystemControlCell

  implicit val propsReuse: Reusability[Props] = Reusability.derive[Props]

  def requestResourceCall(
    id:     Observation.Id,
    stepId: StepId,
    r:      Resource
  ): (ReactMouseEvent, Button.ButtonProps) => Callback =
    (e: ReactMouseEvent, _: Button.ButtonProps) =>
      (e.preventDefaultCB *> e.stopPropagationCB *>
        ObserveCircuit.dispatchCB(RequestResourceRun(id, stepId, r)))
        .unless_(e.altKey || e.button === StepsTable.MiddleButton)

  private val CompletedIcon = IconCheckmark.copy(
    fitted = true,
    clazz = ObserveStyles.completedIcon
  )

  private val RunningIcon = IconCircleNotched.copy(
    fitted = true,
    loading = true,
    clazz = ObserveStyles.runningIcon
  )

  private val FailureIcon = IconAttention.copy(
    fitted = true,
    inverted = true,
    clazz = ObserveStyles.errorIcon
  )

  // We want blue if the resource operation is idle or does not exist: these are equivalent cases.
  private def buttonColor(op: Option[ResourceRunOperation]): SemanticColor =
    op.map {
      case ResourceRunOperation.ResourceRunIdle         => Blue
      case ResourceRunOperation.ResourceRunInFlight(_)  => Yellow
      case ResourceRunOperation.ResourceRunCompleted(_) => Green
      case ResourceRunOperation.ResourceRunFailed(_)    => Red
    }.getOrElse(Blue)

  // If we are running, we want a circular spinning icon.
  // If we are completed, we want a checkmark.
  // Otherwise, no icon.
  private def determineIcon(op: Option[ResourceRunOperation]): Option[Icon] =
    op match {
      case Some(ResourceRunOperation.ResourceRunInFlight(_))  => RunningIcon.some
      case Some(ResourceRunOperation.ResourceRunCompleted(_)) =>
        CompletedIcon.some
      case Some(ResourceRunOperation.ResourceRunFailed(_))    => FailureIcon.some
      case _                                                  => none
    }

  protected val component = ScalaComponent
    .builder[Props]
    .render_P { p =>
      <.div(
        ObserveStyles.notInMobile,
        p.resources.sorted.map { r =>
          val buttonIcon                         = determineIcon(p.resourcesCalls.get(r))
          val labeled: js.UndefOr[LabelPosition] = buttonIcon
            .as(LabelPosition.Left: js.UndefOr[LabelPosition])
            .getOrElse(js.undefined)

          Popup(
            trigger = Button(
              size = Small,
              color = buttonColor(p.resourcesCalls.get(r)),
              disabled = p.resourcesCalls.get(r).exists {
                case ResourceRunOperation.ResourceRunInFlight(_) => true
                case _                                           => false
              },
              labelPosition = labeled,
              icon = buttonIcon.isDefined,
              onClickE =
                if (p.canOperate) requestResourceCall(p.id, p.stepId, r)
                else js.undefined,
              clazz = ObserveStyles.defaultCursor.unless_(p.canOperate)
            )(buttonIcon.whenDefined(identity), r.show)
          )(s"Configure ${r.show}")
        }.toTagMod
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build
}
