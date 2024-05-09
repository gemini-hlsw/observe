// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.services

import cats.Endo
import cats.Eq
import cats.effect.IO
import cats.syntax.all.*
import crystal.*
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.ExecutionConfig
import lucuma.core.model.sequence.ExecutionSequence
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.model.sequence.Step
import monocle.Lens
import monocle.Optional
import observe.model.ClientConfig
import observe.model.ExecutionState
import observe.model.ObservationProgress
import observe.model.enums.ActionStatus
import observe.model.events.ClientEvent
import observe.model.events.ClientEvent.SingleActionState
import observe.ui.model.LoadedObservation
import observe.ui.model.ObservationRequests
import observe.ui.model.RootModelData
import observe.ui.model.enums.ApiStatus
import observe.ui.model.enums.OperationRequest
import observe.ui.model.enums.SyncStatus
import org.typelevel.log4cats.Logger
import observe.model.events.ClientEvent.AtomComplete

trait ServerEventHandler:
  private def logMessage(
    rootModelDataMod: (RootModelData => RootModelData) => IO[Unit],
    msg:              String
  )(using Logger[IO]): IO[Unit] =
    msg match
      case NonEmptyString(nes) => rootModelDataMod(RootModelData.log.modify(_ :+ nes))

  private def atomListOptional[S, D](
    instrumentOptic:   Optional[InstrumentExecutionConfig, ExecutionConfig[S, D]],
    sequenceTypeOptic: Lens[ExecutionConfig[S, D], Option[ExecutionSequence[D]]]
  ): Optional[LoadedObservation, List[Atom[D]]] =
    LoadedObservation.config
      .andThen(Pot.readyPrism)
      .andThen(instrumentOptic)
      .andThen(sequenceTypeOptic)
      .some
      .andThen(ExecutionSequence.possibleFuture)

  private def removeFutureAtomFromLoadedObservation[S, D](
    instrumentOptic:   Optional[InstrumentExecutionConfig, ExecutionConfig[S, D]],
    sequenceTypeOptic: Lens[ExecutionConfig[S, D], Option[ExecutionSequence[D]]],
    atomId:            Atom.Id
  ): LoadedObservation => LoadedObservation =
    atomListOptional(instrumentOptic, sequenceTypeOptic)
      .modify(_.filterNot(_.id === atomId))

  private val gmosNorthExecutionOptional
    : Optional[InstrumentExecutionConfig, ExecutionConfig.GmosNorth] =
    InstrumentExecutionConfig.gmosNorth
      .andThen(InstrumentExecutionConfig.GmosNorth.executionConfig)

  private val gmosSouthExecutionOptional
    : Optional[InstrumentExecutionConfig, ExecutionConfig.GmosSouth] =
    InstrumentExecutionConfig.gmosSouth
      .andThen(InstrumentExecutionConfig.GmosSouth.executionConfig)

  private def sequenceTypeOptic[S, D](
    sequenceType: SequenceType
  ): Lens[ExecutionConfig[S, D], Option[ExecutionSequence[D]]] =
    sequenceType match
      case SequenceType.Acquisition => ExecutionConfig.acquisition[S, D]
      case SequenceType.Science     => ExecutionConfig.science[S, D]

  def instrumentRemoveFutureAtomFromLoadedObservation(
    sequenceType: SequenceType,
    atomId:       Atom.Id
  ): LoadedObservation => LoadedObservation =
    loadedObservation =>
      loadedObservation.config.toOption
        .map(_.instrument)
        .collect:
          case Instrument.GmosNorth =>
            removeFutureAtomFromLoadedObservation(
              gmosNorthExecutionOptional,
              sequenceTypeOptic(sequenceType),
              atomId
            )(loadedObservation)
          case Instrument.GmosSouth =>
            removeFutureAtomFromLoadedObservation(
              gmosSouthExecutionOptional,
              sequenceTypeOptic(sequenceType),
              atomId
            )(loadedObservation)
        .getOrElse(loadedObservation)

  protected def processStreamEvent(
    clientConfigMod:    Endo[Pot[ClientConfig]] => IO[Unit],
    rootModelDataMod:   Endo[RootModelData] => IO[Unit],
    syncStatusMod:      Endo[Option[SyncStatus]] => IO[Unit],
    configApiStatusMod: Endo[ApiStatus] => IO[Unit]
  )(
    event:              ClientEvent
  )(using Logger[IO]): IO[Unit] =
    // IO.println(event) >> {
    event match
      case ClientEvent.BaDum                                                              =>
        IO.unit
      case ClientEvent.InitialEvent(cc)                                                   =>
        clientConfigMod(_ => cc.ready)
      case ClientEvent.SingleActionEvent(obsId, stepId, subsystem, event, error)          =>
        rootModelDataMod(
          (RootModelData.executionState
            .at(obsId)
            .some
            .andThen(ExecutionState.stepResources.at(stepId).some.at(subsystem))
            .replace:
              event match
                case SingleActionState.Started   => ActionStatus.Running.some
                case SingleActionState.Completed => ActionStatus.Completed.some
                case SingleActionState.Failed    => ActionStatus.Failed.some
          )
          >>> // Reset Request
            (RootModelData.obsRequests
              .index(obsId)
              .andThen(ObservationRequests.subsystemRun.index(stepId).index(subsystem))
              .replace(OperationRequest.Idle))
        )
          >> error.map(logMessage(rootModelDataMod, _)).orEmpty
      case ClientEvent.ChecksOverrideEvent(_)                                             =>
        IO.unit // TODO Update the UI
      case ClientEvent.ObserveState(sequenceExecution, conditions, operator, recordedIds) =>
        val nighttimeLoadedObsId = sequenceExecution.headOption.map(_._1)

        rootModelDataMod(
          RootModelData.operator.replace(operator) >>>
            RootModelData.conditions.replace(conditions) >>>
            RootModelData.executionState.replace(sequenceExecution) >>>
            RootModelData.recordedIds.replace(recordedIds) >>>
            // All requests are reset on every state update from the server.
            // Or should we only reset the observations that change? In that case, we need to do a thorough comparison.
            // TODO: Maybe just reset in the ApiImpl when we get the response from the server.
            RootModelData.obsRequests.replace(Map.empty) >>>
            RootModelData.nighttimeObservation.modify: obs =>
              // Only set if loaded obsId changed, otherwise config is lost.
              if (obs.map(_.obsId) =!= nighttimeLoadedObsId)
                nighttimeLoadedObsId.map(LoadedObservation(_))
              else
                obs
        ) >>
          syncStatusMod(_ => SyncStatus.Synced.some) >>
          configApiStatusMod(_ => ApiStatus.Idle)
      case ClientEvent.ProgressEvent(ObservationProgress(obsId, stepProgress))            =>
        rootModelDataMod(RootModelData.obsProgress.at(obsId).replace(stepProgress.some))
      case ClientEvent.AtomComplete(_, _, _)                                              => IO.unit
      case ClientEvent.AtomLoaded(obsId, sequenceType, atomId)                            =>
        rootModelDataMod:
          RootModelData.nighttimeObservation.some.modify:
            instrumentRemoveFutureAtomFromLoadedObservation(sequenceType, atomId)
      // TODO Also requery future sequence here. It may have changed. Or there may be new atoms to load past the limit.
      // We're actually doing it in SequenceTable, but it should be done here, since we only need to do it once per atom,
      // and in SequenceTable it's being done once per step.
      // However, we need to turn the app initialization on its head in MainApp to achieve this.
  // }

  protected def processStreamError(
    rootModelDataMod: (RootModelData => RootModelData) => IO[Unit]
  )(error: Throwable)(using Logger[IO]): IO[Unit] =
    logMessage(rootModelDataMod, s"ERROR Receiving Client Event: ${error.getMessage}")
