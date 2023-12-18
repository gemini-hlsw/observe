// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.*
import crystal.react.*
import crystal.syntax.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.model.sequence.Step
import lucuma.react.common.ReactFnProps
import lucuma.react.common.given
import lucuma.react.primereact.*
import lucuma.ui.DefaultErrorRender
import lucuma.ui.syntax.all.*
import observe.model.ExecutionState
import observe.model.SequenceState
import observe.model.StepProgress
import observe.ui.ObserveStyles
import observe.ui.components.queue.SessionQueue
import observe.ui.model.AppContext
import observe.ui.model.LoadedObservation
import observe.ui.model.RootModel
import observe.ui.model.RootModelData
import observe.ui.model.SessionQueueRow
import observe.ui.model.enums.ClientMode
import observe.ui.model.enums.ObsClass
import observe.ui.services.SequenceApi
import observe.ui.components.sequence.ObsHeader
import observe.ui.model.enums.OperationRequest

case class Home(rootModel: RootModel) extends ReactFnProps(Home.component)

object Home:
  private type Props = Home

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useContext(SequenceApi.ctx)
      .render: (props, ctx, sequenceApi) =>
        import ctx.given

        val rootModelData: RootModelData = props.rootModel.data.get

        val loadedObs: Option[LoadedObservation] = rootModelData.nighttimeObservation

        val loadObservation: Observation.Id => Callback = obsId =>
          rootModelData.readyObservationsMap
            .get(obsId)
            .foldMap: obsRow =>
              props.rootModel.data
                .zoom(RootModelData.nighttimeObservation)
                .set(LoadedObservation(obsId).some) >>
                sequenceApi.loadObservation(obsId, obsRow.instrument).runAsync

        val obsStates: Map[Observation.Id, SequenceState] =
          rootModelData.executionState.view.mapValues(_.sequenceState).toMap

        val clientMode: ClientMode = rootModelData.clientMode

        rootModelData.userVault.map: userVault =>
          <.div(ObserveStyles.MainPanel)(
            Splitter(
              layout = Layout.Vertical,
              stateKey = "main-splitter",
              stateStorage = StateStorage.Local,
              clazz = ObserveStyles.Shrinkable
            )(
              SplitterPanel(clazz = ObserveStyles.TopPanel)(
                rootModelData.readyObservations
                  .map(
                    _.map(obs =>
                      SessionQueueRow(
                        obs,
                        SequenceState.Idle,
                        props.rootModel.data.get.observer,
                        ObsClass.Nighttime,
                        false, // obs.activeStatus === ObsActiveStatus.Active,
                        none,
                        none,
                        false
                      )
                    )
                  )
                  .renderPot(SessionQueue(_, obsStates, loadedObs, loadObservation)),
                ConfigPanel(
                  rootModelData.nighttimeObservation.map(_.obsId),
                  props.rootModel.data.zoom(RootModelData.observer),
                  props.rootModel.data.zoom(RootModelData.operator),
                  props.rootModel.data.zoom(RootModelData.conditions)
                )
              ),
              SplitterPanel()(
                rootModelData.selectedObservation
                  .flatMap(rootModelData.readyObservationsMap.get)
                  .map: selectedObs =>
                    val obsId = selectedObs.obsId

                    val executionStateOpt: ViewOpt[ExecutionState] =
                      props.rootModel.data
                        .zoom(RootModelData.executionState.index(obsId))

                    <.div(ObserveStyles.ObservationArea, ^.key := obsId.toString)(
                      ObsHeader(
                        selectedObs,
                        executionStateOpt.get.exists(_.sequenceState.isRunning),
                        executionStateOpt.zoom(OperationRequest.PauseState)
                      ),
                      loadedObs.map(loadedObs =>
                        loadedObs.config.renderPot( config =>
                          val progress: Option[StepProgress] = rootModelData.obsProgress.get(obsId)

                                val selectedStep: Option[Step.Id] = rootModelData.obsSelectedStep(obsId)

                                val setSelectedStep: Step.Id => Callback = stepId =>
                                  props.rootModel.data
                                    .zoom(RootModelData.userSelectedStep.at(obsId))
                                    .mod: oldStepId =>
                                      if (oldStepId.contains_(stepId)) none else stepId.some

                                ObservationSequence(
                                  summary,
                                  config,
                                  executionState,
                                  progress,
                                  selectedStep,
                                  setSelectedStep,
                                  clientMode
                                )
                              },
                              errorRender = t =>
                                <.div(ObserveStyles.ObservationAreaError)(
                                  DefaultErrorRender(t)
                                )
                            )

                      //   loadedObs.map(obs =>
                      //     val obsId: Observation.Id = obs.obsId

                      //     // If for some reason executionState doesn't contain info for obsId, this could be none
                      //     val executionStateOpt: ViewOpt[ExecutionState] =
                      //       props.rootModel.data
                      //         .zoom(RootModelData.executionState.index(obsId))

                      //     (rootModelData.readyObservationsMap.get(obsId).toPot,
                      //      obs.config,
                      //      executionStateOpt.toOptionView.toPot
                      //     ).tupled
                      //       .renderPot(
                      //         { case (summary, config, executionState) =>
                      //           val progress: Option[StepProgress] = rootModelData.obsProgress.get(obsId)

                      //           val selectedStep: Option[Step.Id] = rootModelData.obsSelectedStep(obsId)

                      //           val setSelectedStep: Step.Id => Callback = stepId =>
                      //             props.rootModel.data
                      //               .zoom(RootModelData.userSelectedStep.at(obsId))
                      //               .mod: oldStepId =>
                      //                 if (oldStepId.contains_(stepId)) none else stepId.some

                      //           ObservationSequence(
                      //             summary,
                      //             config,
                      //             executionState,
                      //             progress,
                      //             selectedStep,
                      //             setSelectedStep,
                      //             clientMode
                      //           )
                      //         },
                      //         errorRender = t =>
                      //           <.div(ObserveStyles.ObservationAreaError)(
                      //             DefaultErrorRender(t)
                      //           )
                      //       )
                      //   )
                      // )
                      )
                    )
              )
            ),
            Accordion(tabs =
              List(
                AccordionTab(clazz = ObserveStyles.LogArea, header = "Show Log")(
                  <.div(^.height := "200px")
                )
              )
            )
          )
