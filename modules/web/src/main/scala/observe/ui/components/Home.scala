// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.Order.given
import cats.effect.IO
import cats.syntax.all.*
import crystal.react.View
import crystal.react.hooks.*
import crystal.react.syntax.effect.*
import crystal.react.syntax.pot.given
import crystal.syntax.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.ExecutionConfig
import lucuma.core.model.sequence.ExecutionSequence
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.model.sequence.Step
import lucuma.core.syntax.display.*
import lucuma.react.common.ReactFnProps
import lucuma.react.common.given
import lucuma.react.primereact.*
import lucuma.schemas.odb.SequenceSQL
import lucuma.ui.reusability.given
import lucuma.ui.syntax.all.*
import lucuma.ui.hooks.*
import observe.model.ClientStatus
import observe.model.ExecutionState
import observe.model.Observer
import observe.model.UserDetails
import observe.model.enums.ActionStatus
import observe.model.enums.Resource
import observe.model.enums.SequenceState
import observe.queries.ObsQueriesGQL
import observe.ui.AppContext
import observe.ui.DefaultErrorPolicy
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.queue.SessionQueue
import observe.ui.model.ResourceRunOperation
import observe.ui.model.RootModel
import observe.ui.model.SessionQueueRow
import observe.ui.model.TabOperations
import observe.ui.model.enums.ObsClass

import scala.collection.immutable.SortedMap

import sequence.{GmosNorthSequenceTables, GmosSouthSequenceTables}
import lucuma.ui.sso.UserVault

case class Home(rootModel: View[RootModel]) extends ReactFnProps(Home.component)

object Home:
  private type Props = Home

  private val clientStatus = ClientStatus.Default.copy(user = UserDetails("telops", "Telops").some)

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useStreamResourceOnMountBy: (_, ctx) =>
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
                Observer("Telops").some,
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
      .useStateView(none[Observation.Id]) // selectedObsId
      .useEffectResultWithDepsBy((_, _, _, selectedObsId) => selectedObsId.get): (_, ctx, _, _) =>
        obsId =>
          import ctx.given

          // TODO We will have to requery under certain conditions:
          // - After step is executed/paused/aborted.
          // - If sequence changes... How do we know this??? Update: Shane will add a hash to the API
          obsId.fold(IO.none)(
            SequenceSQL.SequenceQuery[IO].query(_).map(_.observation.map(_.execution.config))
          )
      // TODO: This will actually come from the observe server.
      .useStateView(
        ExecutionState(SequenceState.Idle, List.empty)
      )
      .useEffectWithDepsBy((_, _, _, _, config, _) => config)((_, _, _, _, _, executionState) =>
        config =>
          def getBreakPoints(sequence: Option[ExecutionSequence[?]]): Set[Step.Id] =
            sequence
              .map(s => s.nextAtom +: s.possibleFuture)
              .orEmpty
              .flatMap(_.steps.toList)
              .collect { case s if s.breakpoint === Breakpoint.Enabled => s.id }
              .toSet

          val configOpt: Option[InstrumentExecutionConfig] = config.toOption.flatten

          // We simulate we are running some step.
          val executingStepId: Option[Step.Id] =
            configOpt.flatMap:
              case InstrumentExecutionConfig.GmosNorth(executionConfig) =>
                executionConfig.acquisition.map(_.nextAtom.steps.head.id)
              case InstrumentExecutionConfig.GmosSouth(executionConfig) =>
                executionConfig.science.map(_.nextAtom.steps.head.id)

          val sequenceState: SequenceState =
            executingStepId.fold(SequenceState.Idle)(stepId =>
              SequenceState.Running(stepId, none, false, false)
            )

          // We simulate a config state.
          val configState = List(
            (Resource.TCS, ActionStatus.Completed),
            (Resource.Gcal, ActionStatus.Running),
            (Resource.Instrument(Instrument.GmosNorth), ActionStatus.Pending)
          )

          val initialBreakpoints: Set[Step.Id] =
            configOpt
              .map:
                case InstrumentExecutionConfig.GmosNorth(executionConfig) =>
                  getBreakPoints(executionConfig.acquisition) ++
                    getBreakPoints(executionConfig.science)
                case InstrumentExecutionConfig.GmosSouth(executionConfig) =>
                  getBreakPoints(executionConfig.acquisition) ++
                    getBreakPoints(executionConfig.science)
              .orEmpty

          executionState.set(ExecutionState(sequenceState, configState, initialBreakpoints))
      )
      .useTheme()
      .render: (props, ctx, observations, selectedObsId, config, executionState, theme) =>
        import ctx.given

        // TODO: Notify server of breakpoint changes
        val flipBreakPoint: Step.Id => Callback = stepId =>
          executionState
            .zoom(ExecutionState.breakpoints)
            .mod(set => if (set.contains(stepId)) set - stepId else set + stepId)

        val executingStepId: Option[Step.Id] = executionState.get.sequenceState match
          case SequenceState.Running(stepId, _, _, _) => stepId.some
          case _                                      => none

        def tabOperations(excutionConfig: ExecutionConfig[?, ?]): TabOperations =
          executingStepId.fold(TabOperations.Default): stepId =>
            TabOperations.Default.copy(resourceRunRequested = SortedMap.from:
              executionState.get.configStatus.flatMap: (resource, status) =>
                ResourceRunOperation.fromActionStatus(stepId)(status).map(resource -> _)
            )

        props.rootModel
          .zoom(RootModel.userVault)
          .mapValue: (userVault: View[UserVault]) =>
            // props.rootModel.get.userVault.map: userVault =>
            <.div(ObserveStyles.MainUI)(
              TopBar(userVault, theme, IO.unit),
              // Divider(position = Divider.Position.HorizontalCenter, clazz = ObserveStyles.Divider)(
              //   "Observe GS"
              // ),
              Splitter(
                layout = Layout.Vertical,
                stateKey = "main-splitter",
                stateStorage = StateStorage.Local,
                clazz = ObserveStyles.Shrinkable
              )(
                SplitterPanel():
                  Splitter(
                    stateKey = "top-splitter",
                    stateStorage = StateStorage.Local,
                    clazz = ObserveStyles.TopPanel
                  )(
                    SplitterPanel(size = 80)(
                      observations.toPot.renderPot(SessionQueue(_, selectedObsId))
                    ),
                    SplitterPanel()(
                      HeadersSideBar(
                        props.rootModel.get.status,
                        props.rootModel.get.operator,
                        props.rootModel.zoom(RootModel.conditions)
                      )
                    )
                  )
                ,
                SplitterPanel():
                  TabView(
                    clazz = ObserveStyles.SequenceTabView,
                    activeIndex = 1,
                    panels = List(
                      TabPanel(
                        clazz = ObserveStyles.SequenceTabPanel,
                        header = React.Fragment(
                          <.span(ObserveStyles.ActiveInstrumentLabel, "Daytime Queue"),
                          Tag(
                            clazz = ObserveStyles.LabelPointer |+| ObserveStyles.IdleTag,
                            icon = Icons.CircleDot,
                            value = "Idle"
                          )
                        )
                      )(
                      )
                    ) ++
                      (observations.toOption, selectedObsId.get).flatMapN: (obsRows, obsId) =>
                        obsRows
                          .find(_.obsId === obsId)
                          .map(obs =>
                            TabPanel(
                              clazz = ObserveStyles.SequenceTabPanel,
                              header = React.Fragment(
                                <.span(
                                  ObserveStyles.ActiveInstrumentLabel,
                                  obs.instrument.shortName
                                ),
                                Tag(
                                  clazz = ObserveStyles.LabelPointer |+| ObserveStyles.RunningTag,
                                  icon = Icons.CircleNotch.withSpin(true),
                                  value = obsId.shortName
                                  // s"${Observation.Id.fromLong(133742).get.shortName} - 3/${observe.demo.DemoExecutionSteps.length}"
                                )
                              )
                            )(
                              config
                                .map(_.toPot)
                                .flatten
                                .renderPot:
                                  case InstrumentExecutionConfig.GmosNorth(config) =>
                                    GmosNorthSequenceTables(
                                      clientStatus,
                                      obsId,
                                      config,
                                      executionState.get,
                                      tabOperations(config),
                                      isPreview = false,
                                      flipBreakPoint
                                    ) // TODO isPreview
                                      .withKey(obsId.toString)
                                  case InstrumentExecutionConfig.GmosSouth(config) =>
                                    GmosSouthSequenceTables(
                                      clientStatus,
                                      obsId,
                                      config,
                                      executionState.get,
                                      tabOperations(config),
                                      isPreview = false,
                                      flipBreakPoint
                                    )
                                      .withKey(obsId.toString)
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
              // Toolbar(
              //   clazz = ObserveStyles.Footer,
              //   left = "Observe - GS",
              //   right = React
              //     .Fragment(
              //       userVault.user.displayName,
              //       ThemeSelector(),
              //       Button(
              //         "Logout",
              //         onClick = ctx.ssoClient.logout.runAsync >>
              //           props.rootModel.zoom(RootModel.userVault).set(none)
              //       )
              //     )
              //     .rawElement
              // )
            )
