// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.Order.given
import cats.syntax.all.*
import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.syntax.display.*
import lucuma.react.common.*
import lucuma.react.fa.FontAwesomeIcon
import lucuma.react.primereact.*
import observe.model.enums.*
import observe.model.given
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.DefaultTooltipOptions
import observe.ui.display.given
import observe.ui.model.AppContext
import observe.ui.model.SubsystemRunOperation
import observe.ui.model.enums.ClientMode
import observe.ui.services.SequenceApi

import scala.collection.immutable.SortedMap

/**
 * Contains the control buttons for each subsystem
 */
case class SubsystemControls(
  obsId:           Observation.Id,
  stepId:          Step.Id,
  subsystems:      List[Resource | Instrument],
  subsystemsCalls: SortedMap[Resource | Instrument, SubsystemRunOperation],
  clientMode:      ClientMode
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

  private val IdleIcon      = Icons.ArrowUpFromLine.withFixedWidth()
  private val RunningIcon   = Icons.CircleNotch.withFixedWidth()
  private val CompletedIcon = Icons.Check.withFixedWidth()
  private val FailureIcon   = Icons.CircleExclamation.withFixedWidth().withInverse()

  // We want blue if the resource operation is idle or does not exist: these are equivalent cases.
  private def buttonSeverity(op: Option[SubsystemRunOperation]): Button.Severity =
    op.map:
      case SubsystemRunOperation.SubsystemRunIdle         => Button.Severity.Primary
      case SubsystemRunOperation.SubsystemRunInFlight(_)  => Button.Severity.Warning
      case SubsystemRunOperation.SubsystemRunCompleted(_) => Button.Severity.Success
      case SubsystemRunOperation.SubsystemRunFailed(_)    => Button.Severity.Danger
    .getOrElse(Button.Severity.Primary)

  // If we are running, we want a circular spinning icon.
  // If we are completed, we want a checkmark.
  // Otherwise, no icon.
  private def determineIcon(op: Option[SubsystemRunOperation]): FontAwesomeIcon =
    op match
      case Some(SubsystemRunOperation.SubsystemRunInFlight(_))  => RunningIcon
      case Some(SubsystemRunOperation.SubsystemRunCompleted(_)) => CompletedIcon
      case Some(SubsystemRunOperation.SubsystemRunFailed(_))    => FailureIcon
      case _                                                    => IdleIcon

  private val component = ScalaFnComponent
    .withHooks[Props]
    .useContext(AppContext.ctx)
    .useContext(SequenceApi.ctx)
    .render: (props, ctx, sequenceApi) =>
      import ctx.given

      <.div(ObserveStyles.ConfigButtonStrip)( // (ObserveStyles.notInMobile)(
        props.subsystems
          .sorted[Resource | Instrument]
          .map: subsystem =>
            val subsystemState: Option[SubsystemRunOperation] = props.subsystemsCalls.get(subsystem)
            val buttonIcon: FontAwesomeIcon                   = determineIcon(subsystemState)

            <.span(
              Button(
                size = Button.Size.Small,
                severity = buttonSeverity(subsystemState),
                disabled = subsystemState.exists:
                  case SubsystemRunOperation.SubsystemRunInFlight(_) => true
                  case _                                             => false
                ,
                clazz = ObserveStyles.ConfigButton |+|
                  ObserveStyles.DefaultCursor.unless_(props.clientMode.canOperate),
                onClickE = _.stopPropagationCB >> sequenceApi
                  .execute(props.obsId, props.stepId, subsystem)
                  .runAsync,
                tooltip = s"Configure ${subsystem.shortName}",
                tooltipOptions = DefaultTooltipOptions
              )(buttonIcon, subsystem.shortName)
            )
          .toTagMod
      )
