// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.*
import lucuma.react.fa.IconSize
import lucuma.react.primereact.Button
import lucuma.react.primereact.InputGroup
import lucuma.react.primereact.TooltipOptions
import observe.model.Observation
import observe.model.enums.RunOverride
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.AppContext
import observe.ui.model.enums.OperationRequest
import observe.ui.services.SequenceApi

case class SeqControlButtons(
  obsId:          Observation.Id,
  isRunning:      Boolean,
  pauseRequested: ViewOpt[OperationRequest]
) extends ReactFnProps(SeqControlButtons.component)

object SeqControlButtons:
  private type Props = SeqControlButtons

  private val tooltipOptions =
    TooltipOptions(position = TooltipOptions.Position.Top, showDelay = 100)

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useContext(SequenceApi.ctx)
      .render: (props, ctx, sequenceApi) =>
        import ctx.given

        InputGroup(
          Button(
            clazz = ObserveStyles.PlayButton |+| ObserveStyles.ObsSummaryButton,
            icon =
              if (props.isRunning)
                Icons.CircleNotch.withFixedWidth().withSize(IconSize.LG).withSpin()
              else
                Icons.Play.withFixedWidth().withSize(IconSize.LG),
            tooltip = "Start/Resume sequence",
            tooltipOptions = tooltipOptions,
            onClick = sequenceApi.start(props.obsId, RunOverride.Override).runAsync,
            disabled = props.isRunning
          ),
          Button(
            clazz = ObserveStyles.PauseButton |+| ObserveStyles.ObsSummaryButton,
            icon =
              // TODO Overlay this if pause pending
              // if (props.isRunning)
              //   Icons.CircleNotch.withFixedWidth().withSize(IconSize.LG).withSpin()
              // else
              Icons.Pause.withFixedWidth().withSize(IconSize.LG),
            tooltip = "Pause sequence",
            tooltipOptions = tooltipOptions,
            onClick = props.pauseRequested
              .set(OperationRequest.InFlight) >> sequenceApi.pause(props.obsId).runAsync,
            disabled = props.pauseRequested.get.contains_(OperationRequest.InFlight)
          ).when(props.isRunning)
        )
      // TODO Cancel pause
