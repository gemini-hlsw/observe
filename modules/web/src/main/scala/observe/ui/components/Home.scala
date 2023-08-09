// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.effect.IO
import cats.syntax.all.*
import crystal.react.View
import crystal.react.hooks.*
import crystal.react.syntax.effect.*
import crystal.syntax.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.syntax.display.*
import lucuma.schemas.odb.SequenceSQL
import lucuma.ui.reusability.given
import lucuma.ui.syntax.all.*
import observe.model.ClientStatus
import observe.model.Observer
import observe.model.RunningStep
import observe.model.UserDetails
import observe.model.enums.SequenceState
import observe.queries.ObsQueriesGQL
import observe.ui.AppContext
import observe.ui.DefaultErrorPolicy
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.queue.SessionQueue
import observe.ui.components.sequence.*
import observe.ui.model.RootModel
import observe.ui.model.SessionQueueRow
import observe.ui.model.TabOperations
import observe.ui.model.enums.ObsClass
import react.common.ReactFnProps
import react.common.given
import react.primereact.*

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
          // - If sequence changes... How do we know this???
          obsId.fold(IO.none)(
            SequenceSQL.SequenceQuery[IO].query(_).map(_.observation.map(_.execution.config))
          )
      .render: (props, ctx, observations, selectedObsId, sequence) =>
        import ctx.given

        props.rootModel.get.userVault.map(userVault =>
          <.div(ObserveStyles.MainUI)(
            Divider(position = Divider.Position.HorizontalCenter, clazz = ObserveStyles.Divider)(
              "Observe GS"
            ),
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
                            sequence
                              .map(_.toPot)
                              .flatten
                              .renderPot:
                                case InstrumentExecutionConfig.GmosNorth(config) =>
                                  GmosNorthStepsTable(
                                    clientStatus,
                                    obsId,
                                    config,
                                    TabOperations.Default,
                                    SequenceState.Running(false, false),
                                    RunningStep
                                      .fromStepId(
                                        config.acquisition.map(_.nextAtom.steps.head.id),
                                        1,
                                        1
                                      ),
                                    none,
                                    isPreview = false
                                  ) // TODO isPreview
                                    .withKey(obsId.toString)
                                case InstrumentExecutionConfig.GmosSouth(config) =>
                                  GmosSouthStepsTable(
                                    clientStatus,
                                    obsId,
                                    config,
                                    TabOperations.Default,
                                    SequenceState.Running(false, false),
                                    RunningStep
                                      .fromStepId(
                                        config.acquisition.map(_.nextAtom.steps.head.id),
                                        1,
                                        1
                                      ),
                                    none,
                                    isPreview = false
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
            ),
            Toolbar(
              clazz = ObserveStyles.Footer,
              left = "Observe - GS",
              right = React
                .Fragment(
                  userVault.user.displayName,
                  ThemeSelector(),
                  Button(
                    "Logout",
                    onClick = ctx.ssoClient.logout.runAsync >>
                      props.rootModel.zoom(RootModel.userVault).set(none)
                  )
                )
                .rawElement
            )
          )
        )
