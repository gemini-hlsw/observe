// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.Order.given
import cats.effect.IO
import cats.syntax.all.*
import crystal.react.*
import crystal.react.hooks.*
import crystal.syntax.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.ExecutionConfig
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.model.sequence.Step
import lucuma.react.common.ReactFnProps
import lucuma.react.common.given
import lucuma.react.primereact.*
import lucuma.ui.syntax.all.*
import monocle.Iso
import observe.model.ExecutionState
import observe.model.SequenceState
import observe.model.enums.Resource
import observe.model.given
import observe.queries.ObsQueriesGQL
import observe.ui.DefaultErrorPolicy
import observe.ui.ObserveStyles
import observe.ui.components.queue.SessionQueue
import observe.ui.model.AppContext
import observe.ui.model.LoadedObservation
import observe.ui.model.ResourceRunOperation
import observe.ui.model.RootModel
import observe.ui.model.RootModelData
import observe.ui.model.SessionQueueRow
import observe.ui.model.TabOperations
import observe.ui.model.enums.ClientMode
import observe.ui.model.enums.ObsClass
import observe.ui.services.SequenceApi

import scala.collection.immutable.SortedMap

import sequence.{GmosNorthSequenceTables, GmosSouthSequenceTables}

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
        import ctx.given

        val loadedObsId: Option[Observation.Id] =
          props.rootModel.data.get.nighttimeObservation.map(_.obsId)

        val loadObservation: Observation.Id => Callback = obsId =>
          props.rootModel.data
            .zoom(RootModelData.nighttimeObservation)
            .set(LoadedObservation(obsId).some)

        val clientMode: ClientMode = props.rootModel.data.get.clientMode

        props.rootModel.data.get.userVault.map: userVault =>
          <.div(ObserveStyles.MainPanel)(
            Splitter(
              layout = Layout.Vertical,
              stateKey = "main-splitter",
              stateStorage = StateStorage.Local,
              clazz = ObserveStyles.Shrinkable
            )(
              SplitterPanel():
                observations.toPot
                  .map(_.filter(_.obsClass == ObsClass.Nighttime))
                  .renderPot(SessionQueue(_, loadedObsId, loadObservation))
              ,
              SplitterPanel()(
                loadedObsId.map(obsId =>
                  // If for some reason sequenceExecution doesn't contain info for obsId, this could be none
                  val executionStateOpt: ViewOpt[ExecutionState] =
                    props.rootModel.data
                      .zoom(RootModelData.sequenceExecution)
                      .zoom(Iso.id[Map[Observation.Id, ExecutionState]].index(obsId))

                  executionStateOpt.mapValue(executionState =>
                    val breakpoints: View[Set[Step.Id]] =
                      executionState.zoom(ExecutionState.breakpoints)

                    val flipBreakPoint: (Observation.Id, Step.Id, Breakpoint) => Callback =
                      (obsId, stepId, value) =>
                        breakpoints
                          .mod(set => if (set.contains(stepId)) set - stepId else set + stepId) >>
                          sequenceApi.setBreakpoint(obsId, stepId, value).runAsync

                    val runningStepId: Option[Step.Id] = executionState.get.runningStepId

                    def tabOperations(excutionConfig: ExecutionConfig[?, ?]): TabOperations =
                      runningStepId.fold(TabOperations.Default): stepId =>
                        TabOperations.Default.copy(resourceRunRequested = SortedMap.from:
                          executionState.get.configStatus.flatMap: (resource, status) =>
                            ResourceRunOperation.fromActionStatus(stepId)(status).map(resource -> _)
                        )

                    <.div(^.height := "100%", ^.key := obsId.toString)(
                      props.rootModel.data.get.nighttimeObservation.toPot
                        .flatMap(_.unPot)
                        .renderPot: (obsId, summary, config) =>
                          React.Fragment(
                            ObsHeader(obsId, summary),
                            config match
                              case InstrumentExecutionConfig.GmosNorth(config) =>
                                GmosNorthSequenceTables(
                                  clientMode,
                                  obsId,
                                  config,
                                  executionState.get,
                                  tabOperations(config),
                                  isPreview = false,
                                  flipBreakPoint
                                )
                              case InstrumentExecutionConfig.GmosSouth(config) =>
                                GmosSouthSequenceTables(
                                  clientMode,
                                  obsId,
                                  config,
                                  executionState.get,
                                  tabOperations(config),
                                  isPreview = false,
                                  flipBreakPoint
                                )
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
