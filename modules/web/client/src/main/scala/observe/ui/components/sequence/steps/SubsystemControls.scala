// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.Order.given
import cats.syntax.all.*
import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.syntax.display.*
import lucuma.react.common.*
import lucuma.react.fa.FontAwesomeIcon
import lucuma.react.primereact.*
import observe.model.SubsystemEnabled
import observe.model.SystemOverrides
import observe.model.enums.*
import observe.model.given
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.DefaultTooltipOptions
import observe.ui.display.given
import observe.ui.model.AppContext
import observe.ui.model.enums.ClientMode
import observe.ui.model.enums.OperationRequest
import observe.ui.services.SequenceApi

/**
 * Contains the control buttons for each subsystem
 */
case class SubsystemControls(
  obsId:             Observation.Id,
  stepId:            Step.Id,
  subsystems:        List[Resource | Instrument],
  subsystemStatus:   Map[Resource | Instrument, ActionStatus],
  subsystemRequests: Map[Resource | Instrument, OperationRequest],
  systemOverrides:   SystemOverrides,
  clientMode:        ClientMode
) extends ReactFnProps(SubsystemControls.component):
  private def subsystemState(subsystem: Resource | Instrument): (ActionStatus, OperationRequest) =
    (subsystemStatus.getOrElse(subsystem, ActionStatus.Pending),
     subsystemRequests.getOrElse(subsystem, OperationRequest.Idle)
    )

  private def isSubsystemEnabled(subsystem: Resource | Instrument): SubsystemEnabled =
    subsystem match
      case Resource.TCS  => systemOverrides.isTcsEnabled
      case Resource.Gcal => systemOverrides.isGcalEnabled
      case _: Instrument => systemOverrides.isInstrumentEnabled
      case _             => SubsystemEnabled.Enabled

  // We want blue if the resource operation is idle or does not exist: these are equivalent cases.
  // If we are running, we want a circular spinning icon.
  // If we are completed, we want a checkmark.
  // Otherwise, no icon.
  def buttonProperties(
    subsystem: Resource | Instrument
  ): (FontAwesomeIcon, Button.Severity, Boolean) = // (icon, severity, disabled)
    subsystemState(subsystem) match
      case (_, OperationRequest.InFlight)                  =>
        (SubsystemControls.RunningIcon, Button.Severity.Warning, true)
      case (ActionStatus.Running | ActionStatus.Paused, _) =>
        (SubsystemControls.RunningIcon, Button.Severity.Warning, true)
      case (ActionStatus.Completed, _)                     =>
        (SubsystemControls.CompletedIcon,
         Button.Severity.Success,
         !isSubsystemEnabled(subsystem).value
        )
      case (ActionStatus.Failed, _)                        =>
        (SubsystemControls.FailureIcon,
         Button.Severity.Danger,
         !isSubsystemEnabled(subsystem).value
        )
      case _                                               =>
        (SubsystemControls.IdleIcon, Button.Severity.Primary, !isSubsystemEnabled(subsystem).value)

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
            val (icon, severity, disabled) = props.buttonProperties(subsystem)

            <.span(^.key := s"config-${subsystem}")(
              Button(
                size = Button.Size.Small,
                severity = severity,
                disabled = disabled,
                clazz = ObserveStyles.ConfigButton |+|
                  ObserveStyles.DefaultCursor.unless_(props.clientMode.canOperate),
                onClickE = _.stopPropagationCB >> sequenceApi
                  .execute(props.obsId, props.stepId, subsystem)
                  .runAsync,
                tooltip = s"Configure ${subsystem.shortName}",
                tooltipOptions = DefaultTooltipOptions
              )(icon, subsystem.shortName)
            )
          .toTagMod
      )
