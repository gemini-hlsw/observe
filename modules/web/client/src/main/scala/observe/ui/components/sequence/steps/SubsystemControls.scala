// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.Order.given
import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.syntax.display.*
import lucuma.react.common.*
import lucuma.react.fa.FontAwesomeIcon
import lucuma.react.floatingui.syntax.*
import lucuma.react.primereact.*
import observe.model.enums.*
import observe.model.given
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.display.given
import observe.ui.model.ResourceRunOperation
import observe.ui.model.enums.ClientMode

import scala.collection.immutable.SortedMap

/**
 * Contains the control buttons for each subsystem
 */
case class SubsystemControls(
  obsId:          Observation.Id,
  stepId:         Step.Id,
  resources:      List[Resource | Instrument],
  resourcesCalls: SortedMap[Resource | Instrument, ResourceRunOperation],
  clientMode:     ClientMode
) extends ReactFnProps(SubsystemControls.component)

object SubsystemControls:
  private type Props = SubsystemControls

  // private def requestResourceCall(
  //   id:       Observation.Id,
  //   stepId:   Step.Id,
  //   resource: Resource
  // ): (ReactMouseEvent, Button.ButtonProps) => Callback =
  //   (e: ReactMouseEvent, _: Button.ButtonProps) =>
  //     (e.preventDefaultCB >> e.stopPropagationCB >>
  //       ObserveCircuit.dispatchCB(RequestResourceRun(id, stepId, resource)))
  //       .unless_(e.altKey || e.button === StepsTable.MiddleButton)

  private val CompletedIcon = Icons.Check.withFixedWidth()

  private val RunningIcon = Icons.CircleNotch.withFixedWidth()

  private val FailureIcon = Icons.CircleExclamation.withFixedWidth().withInverse()

  // We want blue if the resource operation is idle or does not exist: these are equivalent cases.
  private def buttonSeverity(op: Option[ResourceRunOperation]): Button.Severity =
    op.map:
      case ResourceRunOperation.ResourceRunIdle         => Button.Severity.Primary
      case ResourceRunOperation.ResourceRunInFlight(_)  => Button.Severity.Warning
      case ResourceRunOperation.ResourceRunCompleted(_) => Button.Severity.Success
      case ResourceRunOperation.ResourceRunFailed(_)    => Button.Severity.Danger
    .getOrElse(Button.Severity.Primary)

  // If we are running, we want a circular spinning icon.
  // If we are completed, we want a checkmark.
  // Otherwise, no icon.
  private def determineIcon(op: Option[ResourceRunOperation]): Option[FontAwesomeIcon] =
    op match
      case Some(ResourceRunOperation.ResourceRunInFlight(_))  => RunningIcon.some
      case Some(ResourceRunOperation.ResourceRunCompleted(_)) =>
        CompletedIcon.some
      case Some(ResourceRunOperation.ResourceRunFailed(_))    => FailureIcon.some
      case _                                                  => none

  private val component = ScalaFnComponent[Props]: props =>
    <.div(ObserveStyles.ConfigButtonStrip)( // (ObserveStyles.notInMobile)(
      props.resources
        .sorted[Resource | Instrument]
        .map: resource =>
          val resourceState: Option[ResourceRunOperation] = props.resourcesCalls.get(resource)
          val buttonIcon: Option[FontAwesomeIcon]         = determineIcon(resourceState)

          <.span(
            Button(
              size = Button.Size.Small,
              severity = buttonSeverity(resourceState),
              disabled = resourceState.exists:
                case ResourceRunOperation.ResourceRunInFlight(_) => true
                case _                                           => false
              ,
              clazz = ObserveStyles.ConfigButton |+|
                ObserveStyles.DefaultCursor.unless_(props.clientMode.canOperate)
              //     onClickE =
              //       if (p.canOperate) requestResourceCall(p.id, p.stepId, r)
              //       else js.undefined,
            )(buttonIcon, resource.shortName)
          ).withTooltip(s"Configure ${resource.shortName}")
        .toTagMod
    )
