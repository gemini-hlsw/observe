// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.Order.given
import cats.effect.IO
import cats.syntax.all.*
import crystal.react.*
import crystal.react.hooks.*
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
import lucuma.react.common.ReactFnProps
import lucuma.react.common.given
import lucuma.react.primereact.*
import lucuma.schemas.odb.SequenceSQL
import lucuma.ui.reusability.given
import lucuma.ui.syntax.all.*
import observe.model.ExecutionState
import observe.model.SequenceState
import observe.model.SystemOverrides
import observe.model.enums.ActionStatus
import observe.model.enums.Resource
import observe.model.given
import observe.queries.ObsQueriesGQL
import observe.ui.DefaultErrorPolicy
import observe.ui.ObserveStyles
import observe.ui.components.queue.SessionQueue
import observe.ui.model.AppContext
import observe.ui.model.ResourceRunOperation
import observe.ui.model.RootModel
import observe.ui.model.SessionQueueRow
import observe.ui.model.TabOperations
import observe.ui.model.enums.ClientMode
import observe.ui.model.enums.ObsClass
import observe.ui.services.SequenceApi

import scala.collection.immutable.SortedMap

import sequence.{GmosNorthSequenceTables, GmosSouthSequenceTables}

case class Home(rootModel: View[RootModel]) extends ReactFnProps(Home.component)

object Home:
  private type Props = Home

  // private val clientStatus = ClientStatus.Default.copy(user = UserDetails("telops", "Telops").some)

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
                props.rootModel.get.observer,
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
        ExecutionState(SequenceState.Idle, none, none, Map.empty, SystemOverrides.AllEnabled)
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
            executingStepId.fold(SequenceState.Idle)(_ => SequenceState.Running(false, false))

          // We simulate a config state.
          val configState = List(
            (Resource.TCS, ActionStatus.Completed),
            (Resource.Gcal, ActionStatus.Running),
            (Instrument.GmosNorth, ActionStatus.Pending)
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

          // TODO Verify it is correct the use of systemOverrides
          executionState.set(
            ExecutionState(
              sequenceState,
              executingStepId,
              none,
              configState.toMap,
              executionState.get.systemOverrides,
              initialBreakpoints
            )
          )
      )
      .useContext(SequenceApi.ctx)
      .render: (props, ctx, observations, selectedObsId, config, executionState, sequenceApi) =>
        import ctx.given

        val breakpoints: View[Set[Step.Id]] = executionState.zoom(ExecutionState.breakpoints)

        // UI is updated optimistically but it's updated again after server response is received.
        val flipBreakPoint: (Observation.Id, Step.Id, Breakpoint) => Callback =
          (obsId, stepId, value) =>
            breakpoints.mod(set => if (set.contains(stepId)) set - stepId else set + stepId) >>
              sequenceApi.setBreakpoint(obsId, stepId, value).runAsync

        val runningStepId: Option[Step.Id] = executionState.get.runningStepId

        val clientMode: ClientMode = props.rootModel.get.clientMode

        def tabOperations(excutionConfig: ExecutionConfig[?, ?]): TabOperations =
          runningStepId.fold(TabOperations.Default): stepId =>
            TabOperations.Default.copy(resourceRunRequested = SortedMap.from:
              executionState.get.configStatus.flatMap: (resource, status) =>
                ResourceRunOperation.fromActionStatus(stepId)(status).map(resource -> _)
            )

        props.rootModel.get.userVault.map: userVault =>
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
                  .renderPot(SessionQueue(_, selectedObsId))
              ,
              SplitterPanel():
                (observations.toOption, selectedObsId.get).mapN: (obsRows, obsId) =>
                  config
                    .map(_.toPot)
                    .flatten
                    .renderPot:
                      case InstrumentExecutionConfig.GmosNorth(config) =>
                        GmosNorthSequenceTables(
                          clientMode,
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
                          clientMode,
                          obsId,
                          config,
                          executionState.get,
                          tabOperations(config),
                          isPreview = false,
                          flipBreakPoint
                        )
                          .withKey(obsId.toString)
            ),
            Accordion(tabs =
              List(
                AccordionTab(clazz = ObserveStyles.LogArea, header = "Show Log")(
                  <.div(^.height := "200px")
                )
              )
            )
          )
