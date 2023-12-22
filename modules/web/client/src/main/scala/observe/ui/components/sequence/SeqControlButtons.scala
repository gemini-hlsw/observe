// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import crystal.Pot
import crystal.*
import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.*
import lucuma.react.fa.IconSize
import lucuma.react.primereact.Button
import lucuma.react.primereact.InputGroup
import lucuma.react.primereact.TooltipOptions
import observe.model.Observation
import observe.model.SequenceState
import observe.model.enums.RunOverride
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.AppContext
import observe.ui.model.ObservationRequests
import observe.ui.model.enums.OperationRequest
import observe.ui.services.SequenceApi

case class SeqControlButtons(
  obsId:         Observation.Id,
  loadedObsId:   Option[Pot[Observation.Id]],
  loadObs:       Observation.Id => Callback,
  sequenceState: SequenceState,
  requests:      ObservationRequests
) extends ReactFnProps(SeqControlButtons.component):
  val isPaused: Boolean =
    requests.pause === OperationRequest.InFlight || sequenceState.userStopRequested

  val isRunning: Boolean = sequenceState.isRunning

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

        val selectedObsIsLoaded: Boolean = props.loadedObsId.contains_(props.obsId.ready)

        InputGroup(
          Button(
            clazz = ObserveStyles.PlayButton |+| ObserveStyles.ObsSummaryButton,
            loading = props.loadedObsId.exists(_.isPending),
            icon = Icons.FileArrowUp.withFixedWidth().withSize(IconSize.LG),
            loadingIcon = Icons.CircleNotch.withFixedWidth().withSize(IconSize.LG).withSpin(),
            tooltip = "Load sequence",
            tooltipOptions = tooltipOptions,
            onClick = props.loadObs(props.obsId),
            disabled = props.loadedObsId.exists(!_.isReady)
          ).when(!selectedObsIsLoaded),
          Button(
            clazz = ObserveStyles.PlayButton |+| ObserveStyles.ObsSummaryButton,
            loading = props.isRunning,
            icon = Icons.Play.withFixedWidth().withSize(IconSize.LG),
            loadingIcon = Icons.CircleNotch.withFixedWidth().withSize(IconSize.LG).withSpin(),
            tooltip = "Start/Resume sequence",
            tooltipOptions = tooltipOptions,
            onClick = sequenceApi.start(props.obsId, RunOverride.Override).runAsync,
            disabled = props.isRunning
          ).when(selectedObsIsLoaded),
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
            onClick = sequenceApi.pause(props.obsId).runAsync,
            disabled = props.isPaused
          ).when(selectedObsIsLoaded && props.isRunning)
        )
    // TODO Cancel pause
