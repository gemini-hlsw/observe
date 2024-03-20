// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.*
import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Breakpoint
import lucuma.core.model.Observation
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.model.sequence.Step
import lucuma.react.common.ReactFnProps
import lucuma.schemas.model.ExecutionVisits
import lucuma.ui.DefaultErrorRender
import observe.model.ExecutionState
import observe.model.StepProgress
import observe.ui.ObserveStyles
import observe.ui.components.sequence.GmosNorthSequenceTable
import observe.ui.components.sequence.GmosSouthSequenceTable
import observe.ui.model.AppContext
import observe.ui.model.ObservationRequests
import observe.ui.model.enums.ClientMode
import observe.ui.services.SequenceApi

case class ObservationSequence(
  obsId:           Observation.Id,
  config:          InstrumentExecutionConfig,
  visits:          ExecutionVisits,
  executionState:  View[ExecutionState],
  progress:        Option[StepProgress],
  requests:        ObservationRequests,
  selectedStep:    Option[Step.Id],
  setSelectedStep: Step.Id => Callback,
  clientMode:      ClientMode
) extends ReactFnProps(ObservationSequence.component)

object ObservationSequence:
  private type Props = ObservationSequence

  private val component = ScalaFnComponent
    .withHooks[Props]
    .useContext(AppContext.ctx)
    .useContext(SequenceApi.ctx)
    .render: (props, ctx, sequenceApi) =>
      import ctx.given

      val breakpoints: View[Set[Step.Id]] =
        props.executionState.zoom(ExecutionState.breakpoints)

      val flipBreakPoint: (Observation.Id, Step.Id, Breakpoint) => Callback =
        (obsId, stepId, value) =>
          breakpoints
            .mod(set => if (set.contains(stepId)) set - stepId else set + stepId) >>
            sequenceApi.setBreakpoint(obsId, stepId, value).runAsync

      (props.config, props.visits) match
        case (InstrumentExecutionConfig.GmosNorth(config), ExecutionVisits.GmosNorth(_, visits)) =>
          GmosNorthSequenceTable(
            props.clientMode,
            props.obsId,
            config,
            visits,
            props.executionState.get,
            props.progress,
            props.selectedStep,
            props.setSelectedStep,
            props.requests,
            isPreview = false,
            flipBreakPoint
          )
        case (InstrumentExecutionConfig.GmosSouth(config), ExecutionVisits.GmosSouth(_, visits)) =>
          GmosSouthSequenceTable(
            props.clientMode,
            props.obsId,
            config,
            visits,
            props.executionState.get,
            props.progress,
            props.selectedStep,
            props.setSelectedStep,
            props.requests,
            isPreview = false,
            flipBreakPoint
          )
        case _                                                                                   =>
          <.div(ObserveStyles.ObservationAreaError)(
            DefaultErrorRender(new Exception("Sequence <-> Visits Instrument mismatch!"))
          )
