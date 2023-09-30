// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.Order.given
import cats.effect.IO
import cats.syntax.all.*
import crystal.react.View
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
import observe.ui.model.enums.ClientMode
import observe.model.ExecutionState
import observe.model.Observer
import observe.model.enums.ActionStatus
import observe.model.enums.Resource
import observe.model.enums.SequenceState
import observe.queries.ObsQueriesGQL
import observe.ui.AppContext
import observe.ui.DefaultErrorPolicy
import observe.ui.ObserveStyles
import observe.ui.components.queue.SessionQueue
import observe.ui.model.ResourceRunOperation
import observe.ui.model.RootModel
import observe.ui.model.SessionQueueRow
import observe.ui.model.TabOperations
import observe.ui.model.enums.ObsClass

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
            (Resource.fromInstrument(Instrument.GmosNorth).get, ActionStatus.Pending)
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
      .render: (props, ctx, observations, selectedObsId, config, executionState) =>
        // TODO: Notify server of breakpoint changes
        val flipBreakPoint: Step.Id => Callback = stepId =>
          executionState
            .zoom(ExecutionState.breakpoints)
            .mod(set => if (set.contains(stepId)) set - stepId else set + stepId)

        val executingStepId: Option[Step.Id] = executionState.get.sequenceState match
          case SequenceState.Running(stepId, _, _, _) => stepId.some
          case _                                      => none

        val clientMode: ClientMode = props.rootModel.get.clientMode

        def tabOperations(excutionConfig: ExecutionConfig[?, ?]): TabOperations =
          executingStepId.fold(TabOperations.Default): stepId =>
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
