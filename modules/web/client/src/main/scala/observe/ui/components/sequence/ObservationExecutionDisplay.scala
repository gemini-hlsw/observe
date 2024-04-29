// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import crystal.Pot
import crystal.react.*
import crystal.syntax.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.model.sequence.Step
import lucuma.react.common.*
import lucuma.schemas.model.ExecutionVisits
import lucuma.ui.DefaultErrorRender
import lucuma.ui.syntax.all.*
import observe.model.ExecutionState
import observe.model.SequenceState
import observe.model.StepProgress
import observe.ui.ObserveStyles
import observe.ui.components.ObservationSequence
import observe.ui.model.*
import observe.ui.model.ObsSummary

case class ObservationExecutionDisplay(
  selectedObs:     ObsSummary,
  rootModelData:       View[RootModelData],
  loadObservation: Observation.Id => Callback
) extends ReactFnProps(ObservationExecutionDisplay.component)

object ObservationExecutionDisplay:
  private type Props = ObservationExecutionDisplay

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useState[VdomNode](EmptyVdom) // expandButton
      .render: (props, expandButton) =>
        val selectedObsId                        = props.selectedObs.obsId
        val rootModelData: RootModelData         = props.rootModelData.get

        val executionStateOpt: ViewOpt[ExecutionState] =
          props.rootModelData
            .zoom(RootModelData.executionState.index(selectedObsId))

        val executionStateAndConfig: Option[
          Pot[
            (Observation.Id, InstrumentExecutionConfig, ExecutionVisits, View[ExecutionState])
          ]
        ] =
          rootModelData.nighttimeObservation.map: lo =>
            (lo.obsId.ready, lo.config, lo.visits, executionStateOpt.toOptionView.toPot).tupled

        <.div(ObserveStyles.ObservationArea, ^.key := selectedObsId.toString)(
          ObsHeader(
            props.selectedObs,
            executionStateAndConfig.map(_.map(_._1)),
            props.loadObservation,
            executionStateOpt.get.map(_.sequenceState).getOrElse(SequenceState.Idle),
            rootModelData.obsRequests.getOrElse(
              selectedObsId,
              ObservationRequests.Idle
            ),
            executionStateAndConfig
              .flatMap(_.toOption.map(_._4.zoom(ExecutionState.systemOverrides))),
            expandButton.value
          ),
          // TODO, If ODB cannot generate a sequence, we still show PENDING instead of ERROR
          executionStateAndConfig.map(
            _.renderPot(
              { (loadedObsId, config, visits, executionState) =>
                val progress: Option[StepProgress] =
                  rootModelData.obsProgress.get(loadedObsId)

                val requests: ObservationRequests =
                  rootModelData.obsRequests.getOrElse(loadedObsId, ObservationRequests.Idle)

                val selectedStep: Option[Step.Id] =
                  rootModelData.obsSelectedStep(loadedObsId)

                val setSelectedStep: Step.Id => Callback = stepId =>
                  props.rootModelData
                    .zoom(RootModelData.userSelectedStep.at(loadedObsId))
                    .mod: oldStepId =>
                      if (oldStepId.contains_(stepId)) none else stepId.some

                ObservationSequence(
                  loadedObsId,
                  config,
                  visits,
                  executionState,
                  progress,
                  requests,
                  selectedStep,
                  setSelectedStep,
                  rootModelData.clientMode,
                  expandButton.setState
                )
              },
              errorRender = t =>
                <.div(ObserveStyles.ObservationAreaError)(
                  DefaultErrorRender(t)
                )
            )
          )
        )
