// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.services

import cats.Endo
import cats.effect.IO
import cats.syntax.all.*
import crystal.*
import crystal.react.*
import eu.timepit.refined.types.string.NonEmptyString
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.ExecutionConfig
import lucuma.core.model.sequence.ExecutionSequence
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.model.sequence.Step
import lucuma.react.primereact.Message
import lucuma.react.primereact.MessageItem
import lucuma.react.primereact.ToastRef
import lucuma.ui.LucumaStyles
import lucuma.ui.sequence.SequenceData
import monocle.Lens
import monocle.Optional
import observe.cats.given
import observe.model.ClientConfig
import observe.model.ExecutionState
import observe.model.LogMessage
import observe.model.Notification
import observe.model.ObservationProgress
import observe.model.enums.ActionStatus
import observe.model.enums.ObserveLogLevel
import observe.model.events.ClientEvent
import observe.model.events.ClientEvent.LogEvent
import observe.model.events.ClientEvent.SingleActionState
import observe.model.events.ClientEvent.UserNotification
import observe.ui.model.IsAudioActivated
import observe.ui.model.LoadedObservation
import observe.ui.model.ObservationRequests
import observe.ui.model.RootModelData
import observe.ui.model.enums.ApiStatus
import observe.ui.model.enums.OperationRequest
import observe.ui.model.enums.SyncStatus
import observe.ui.utils.Audio

trait ServerEventHandler:
  private def logMessage(
    rootModelDataMod: (RootModelData => RootModelData) => IO[Unit],
    logLevel:         ObserveLogLevel,
    msg:              String
  ): IO[Unit] =
    msg match
      case NonEmptyString(nes) =>
        LogMessage
          .now[IO](logLevel, nes.value)
          .flatMap: logMsg =>
            rootModelDataMod(RootModelData.globalLog.modify(_.append(logMsg)))

  private def atomListOptional[S, D](
    instrumentOptic:   Optional[InstrumentExecutionConfig, ExecutionConfig[S, D]],
    sequenceTypeOptic: Lens[ExecutionConfig[S, D], Option[ExecutionSequence[D]]]
  ): Optional[LoadedObservation, List[Atom[D]]] =
    LoadedObservation.sequenceData
      .andThen(Pot.readyPrism)
      .andThen(SequenceData.config)
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

  private val flamingos2ExecutionOptional
    : Optional[InstrumentExecutionConfig, ExecutionConfig.Flamingos2] =
    InstrumentExecutionConfig.flamingos2
      .andThen(InstrumentExecutionConfig.Flamingos2.executionConfig)

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
      loadedObservation.sequenceData.toOption
        .map(_.config.instrument)
        .collect:
          case Instrument.Flamingos2 =>
            removeFutureAtomFromLoadedObservation(
              flamingos2ExecutionOptional,
              sequenceTypeOptic(sequenceType),
              atomId
            )(loadedObservation)
          case Instrument.GmosNorth  =>
            removeFutureAtomFromLoadedObservation(
              gmosNorthExecutionOptional,
              sequenceTypeOptic(sequenceType),
              atomId
            )(loadedObservation)
          case Instrument.GmosSouth  =>
            removeFutureAtomFromLoadedObservation(
              gmosSouthExecutionOptional,
              sequenceTypeOptic(sequenceType),
              atomId
            )(loadedObservation)
        .getOrElse(loadedObservation)

  private def showToast(toast: ToastRef, msgs: List[String]): IO[Unit] =
    val node: VdomNode = <.span(msgs.mkTagMod(<.br))
    toast
      .show:
        MessageItem(
          content = node,
          severity = Message.Severity.Error,
          sticky = true,
          clazz = LucumaStyles.Toast
        )
      .to[IO]

  protected def showToast(
    toast: ToastRef,
    msg:   String
  ): IO[Unit] =
    showToast(toast, List(msg))

  protected def processStreamEvent(
    clientConfigMod:    Endo[Pot[ClientConfig]] => IO[Unit],
    rootModelDataMod:   Endo[RootModelData] => IO[Unit],
    syncStatusMod:      Endo[Option[SyncStatus]] => IO[Unit],
    configApiStatusMod: Endo[ApiStatus] => IO[Unit],
    isAudioActivated:   IO[IsAudioActivated],
    toast:              ToastRef
  )(
    event:              ClientEvent
  ): IO[Unit] =
    def playAudio(sound: Audio): IO[Unit] = sound.play.when(isAudioActivated.map(a => a: Boolean))

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
          >> error.map(logMessage(rootModelDataMod, ObserveLogLevel.Error, _)).orEmpty
      case ClientEvent.ChecksOverrideEvent(_)                                             =>
        IO.unit // TODO Update the UI
      case ClientEvent.ObserveState(sequenceExecution, conditions, operator, recordedIds) =>
        rootModelDataMod(
          RootModelData.operator.replace(operator) >>>
            RootModelData.conditions.replace(conditions) >>>
            RootModelData.executionState.replace(sequenceExecution) >>>
            RootModelData.recordedIds.replace(recordedIds) >>>
            // All requests are reset on every state update from the server.
            // Or should we only reset the observations that change? In that case, we need to do a thorough comparison.
            // TODO: Maybe just reset in the ApiImpl when we get the response from the server.
            RootModelData.obsRequests.replace(Map.empty) >>>
            RootModelData.loadedObservations.each
              .andThen(LoadedObservation.refreshing)
              .replace(false) >>>
            (_.withAdjustedLoadedObservations(sequenceExecution.keySet)) >>>
            sequenceExecution
              .collect:
                case (obsId, execState) if !execState.sequenceState.isRunning =>
                  RootModelData.obsProgress.at(obsId).replace(none)
              .toList
              .combineAll
        ) >>
          syncStatusMod(_ => SyncStatus.Synced.some) >>
          configApiStatusMod(_ => ApiStatus.Idle)
      case ClientEvent.StepComplete(_)                                                    =>
        playAudio(Audio.StepBeep)
      case ClientEvent.SequencePaused(_)                                                  =>
        playAudio(Audio.SequencePaused)
      case ClientEvent.BreakpointReached(_)                                               =>
        playAudio(Audio.SequencePaused)
      case ClientEvent.AcquisitionPromptReached(_)                                        =>
        playAudio(Audio.AcquisitionPrompt)
      case ClientEvent.SequenceComplete(_)                                                =>
        playAudio(Audio.SequenceComplete)
      case ClientEvent.SequenceFailed(_, errorMsg)                                        =>
        showToast(toast, List(errorMsg)) >> playAudio(Audio.SequenceError)
      case ClientEvent.ProgressEvent(ObservationProgress(obsId, stepProgress))            =>
        rootModelDataMod(RootModelData.obsProgress.at(obsId).replace(stepProgress.some)) // >>
      case ClientEvent.AtomLoaded(obsId, sequenceType, atomId)                            =>
        rootModelDataMod:
          RootModelData.loadedObservations
            .index(obsId)
            .modify:
              instrumentRemoveFutureAtomFromLoadedObservation(sequenceType, atomId)
      // TODO Also requery future sequence here. It may have changed. Or there may be new atoms to load past the limit.
      // We're actually doing it in SequenceTable, but it should be done here, since we only need to do it once per atom,
      // and in SequenceTable it's being done once per step.
      // However, we need to turn the app initialization on its head in MainApp to achieve this.
      case UserNotification(notification)                                                 =>
        val msgs: IO[List[String]] =
          notification match
            case Notification.ResourceConflict(obsId)                =>
              List(s"Error in observation $obsId: Resource already in use").pure[IO]
            case Notification.InstrumentInUse(obsId, ins)            =>
              List(s"Error in observation $obsId: Instrument $ins already in use").pure[IO]
            case Notification.LoadingFailed(obsId, msgs)             =>
              rootModelDataMod:
                RootModelData.loadedObservations
                  .index(obsId)
                  .modify:
                    LoadedObservation.errorMsg.replace(msgs.mkString("; ").some)
              .as(msgs)
            case Notification.SubsystemBusy(obsId, stepId, resource) =>
              List(s"Error in observation $obsId, step $stepId: Subsystem $resource already in use")
                .pure[IO]

        msgs.flatMap: ms =>
          showToast(toast, ms) >>
            logMessage(rootModelDataMod, ObserveLogLevel.Error, ms.mkString("; "))
      case LogEvent(msg)                                                                  =>
        showToast(toast, List(msg.msg)).whenA(msg.level === ObserveLogLevel.Error) >>
          logMessage(rootModelDataMod, msg.level, msg.msg)

  protected def processStreamError(
    rootModelDataMod: (RootModelData => RootModelData) => IO[Unit]
  )(error: Throwable): IO[Unit] =
    logMessage(
      rootModelDataMod,
      ObserveLogLevel.Error,
      s"ERROR Receiving Client Event: ${error.getMessage}"
    )
