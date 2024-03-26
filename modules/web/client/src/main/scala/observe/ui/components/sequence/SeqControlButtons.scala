// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import crystal.*
import crystal.Pot
import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.*
import lucuma.react.fa.IconSize
import lucuma.react.primereact.Button
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
  val isUserStopRequested: Boolean = sequenceState.userStopRequested

  val isPauseInFlight: Boolean = requests.pause === OperationRequest.InFlight

  val isCancelPauseInFlight: Boolean = requests.cancelPause === OperationRequest.InFlight

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

        <.span(
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
            icon = Icons.Play.withFixedWidth().withSize(IconSize.LG),
            loadingIcon = Icons.CircleNotch.withFixedWidth().withSize(IconSize.LG).withSpin(),
            tooltip = "Start/Resume sequence",
            tooltipOptions = tooltipOptions,
            onClick = sequenceApi.start(props.obsId, RunOverride.Override).runAsync
          ).when(selectedObsIsLoaded && !props.isRunning),
          Button(
            clazz = ObserveStyles.PauseButton |+| ObserveStyles.ObsSummaryButton,
            icon = Icons.Pause.withFixedWidth().withSize(IconSize.LG),
            tooltip = "Pause sequence after current exposure",
            tooltipOptions = tooltipOptions,
            onClick = sequenceApi.pause(props.obsId).runAsync,
            disabled = props.isPauseInFlight
          ).when(selectedObsIsLoaded && props.isRunning && !props.isUserStopRequested),
          Button(
            clazz = ObserveStyles.CancelPauseButton |+| ObserveStyles.ObsSummaryButton,
            icon = Icons.CancelPause.withFixedWidth().withSize(IconSize.LG),
            tooltip = "Cancel process to pause the sequence",
            tooltipOptions = tooltipOptions,
            onClick = sequenceApi.cancelPause(props.obsId).runAsync,
            disabled = props.isCancelPauseInFlight
          ).when(selectedObsIsLoaded && props.isRunning && props.isUserStopRequested)
        )
