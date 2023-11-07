// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.effect.IO
import cats.syntax.all.*
import crystal.react.*
import crystal.react.hooks.*
import crystal.syntax.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
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
import observe.queries.ObsQueriesGQL
import observe.ui.DefaultErrorPolicy
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

case class Home(rootModel: RootModel) extends ReactFnProps(Home.component)

object Home:
  private type Props = Home

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useStreamResourceOnMountBy: (props, ctx) =>
        import ctx.given

        ObsQueriesGQL
          .ActiveObservationIdsQuery[IO]
          .query()
          .map(
            _.observations.matches.map(obs =>
              SessionQueueRow(
                obs.id,
                SequenceState.Idle,
                obs.instrument.getOrElse(Instrument.Visitor),
                obs.title.some,
                props.rootModel.data.get.observer,
                obs.subtitle.map(_.value).orEmpty,
                ObsClass.Nighttime,
                // obs.activeStatus === ObsActiveStatus.Active,
                false,
                none,
                none,
                false
              )
            )
          )
          .reRunOnResourceSignals(ObsQueriesGQL.ObservationEditSubscription.subscribe[IO]())
      .useContext(SequenceApi.ctx)
      .render: (props, ctx, observations, sequenceApi) =>
        val loadedObs: Option[LoadedObservation] =
          props.rootModel.data.get.nighttimeObservation

        val loadObservation: Observation.Id => Callback = obsId =>
          props.rootModel.data
            .zoom(RootModelData.nighttimeObservation)
            .set(LoadedObservation(obsId).some)

        val obsStates: Map[Observation.Id, SequenceState] =
          props.rootModel.data.get.sequenceExecution.view.mapValues(_.sequenceState).toMap

        val clientMode: ClientMode = props.rootModel.data.get.clientMode

        props.rootModel.data.get.userVault.map: userVault =>
          <.div(ObserveStyles.MainPanel)(
            Splitter(
              layout = Layout.Vertical,
              stateKey = "main-splitter",
              stateStorage = StateStorage.Local,
              clazz = ObserveStyles.Shrinkable
            )(
              SplitterPanel()(
                observations.toPot
                  .map(_.filter(_.obsClass == ObsClass.Nighttime))
                  .renderPot(SessionQueue(_, obsStates, loadedObs, loadObservation)),
                ConfigPanel(
                  props.rootModel.data.zoom(RootModelData.observer).toOptionView,
                  props.rootModel.data.zoom(RootModelData.conditions)
                )
              )(^.display.flex, ^.flexDirection.column), // TODO Move to a Style
              SplitterPanel()(
                loadedObs.map(obs =>
                  val obsId = obs.obsId

                  // If for some reason sequenceExecution doesn't contain info for obsId, this could be none
                  val executionStateOpt: ViewOpt[ExecutionState] =
                    props.rootModel.data
                      .zoom(RootModelData.sequenceExecution.index(obsId))

                  (obs.unPot, executionStateOpt.toOptionView.toPot).tupled
                    .renderPot(
                      { case ((obsId, summary, config), executionState) =>
                        val selectedStep: Option[Step.Id] =
                          props.rootModel.data.get.obsSelectedStep(obsId)

                        val setSelectedStep: Step.Id => Callback = stepId =>
                          props.rootModel.data
                            .zoom(RootModelData.userSelectedStep.at(obsId))
                            .mod: oldStepId =>
                              if (oldStepId.contains_(stepId)) none else stepId.some

                        ObservationSequence(
                          obsId,
                          summary,
                          config,
                          executionState,
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
            ),
            Accordion(tabs =
              List(
                AccordionTab(clazz = ObserveStyles.LogArea, header = "Show Log")(
                  <.div(^.height := "200px")
                )
              )
            )
          )
