// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.*
import crystal.Pot
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
import observe.ui.components.sequence.ObsHeader
import observe.ui.model.AppContext
import observe.ui.model.LoadedObservation
import observe.ui.model.ObservationRequests
import observe.ui.model.RootModel
import observe.ui.model.RootModelData
import observe.ui.model.SessionQueueRow
import observe.ui.model.enums.ClientMode
import observe.ui.model.enums.ObsClass
import observe.ui.services.SequenceApi

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

        val selectObservation: Observation.Id => Callback =
          obsId =>
            props.rootModel.data
              .zoom(RootModelData.selectedObservation)
              .set(obsId.some)

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

        rootModelData.userVault.map(_.toPot).flatten.renderPot: userVault =>
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
                  .renderPot(
                    SessionQueue(_, obsStates, selectObservation, loadedObs, loadObservation)
                  ),
                ConfigPanel(
                  rootModelData.nighttimeObservation.map(_.obsId),
                  props.rootModel.data.zoom(RootModelData.observer),
                  props.rootModel.data.zoom(RootModelData.operator),
                  props.rootModel.data.zoom(RootModelData.conditions)
                )
              ),
              SplitterPanel()(
                rootModelData.nighttimeDisplayedObservation
                  .map: selectedObs =>
                    val selectedObsId = selectedObs.obsId

                    val executionStateOpt: ViewOpt[ExecutionState] =
                      props.rootModel.data
                        .zoom(RootModelData.executionState.index(selectedObsId))

                    val executionStateAndConfig: Option[
                      Pot[(Observation.Id, InstrumentExecutionConfig, View[ExecutionState])]
                    ] =
                      loadedObs.map: lo =>
                        (lo.obsId.ready, lo.config, executionStateOpt.toOptionView.toPot).tupled

                    <.div(ObserveStyles.ObservationArea, ^.key := selectedObsId.toString)(
                      ObsHeader(
                        selectedObs,
                        executionStateAndConfig.map(_.map(_._1)),
                        loadObservation,
                        executionStateOpt.get.map(_.sequenceState).getOrElse(SequenceState.Idle),
                        rootModelData.obsRequests.getOrElse(selectedObsId, ObservationRequests.Idle)
                      ),
                      // TODO, If ODB cannot generate a sequence, we still show PENDING instead of ERROR
                      executionStateAndConfig.map(
                        _.renderPot(
                          { (loadedObsId, config, executionState) =>
                            val progress: Option[StepProgress] =
                              rootModelData.obsProgress.get(loadedObsId)

                            val requests: ObservationRequests =
                              rootModelData.obsRequests.getOrElse(loadedObsId,
                                                                  ObservationRequests.Idle
                              )

                            val selectedStep: Option[Step.Id] =
                              rootModelData.obsSelectedStep(loadedObsId)

                            val setSelectedStep: Step.Id => Callback = stepId =>
                              props.rootModel.data
                                .zoom(RootModelData.userSelectedStep.at(loadedObsId))
                                .mod: oldStepId =>
                                  if (oldStepId.contains_(stepId)) none else stepId.some

                            ObservationSequence(
                              loadedObsId,
                              config,
                              executionState,
                              progress,
                              requests,
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
