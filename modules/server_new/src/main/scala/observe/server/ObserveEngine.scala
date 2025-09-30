// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Endo
import cats.Monoid
import cats.effect.Async
import cats.effect.MonadCancelThrow
import cats.effect.Ref
import cats.effect.Sync
import cats.effect.Temporal
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.enums.Site
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.CloudExtinction
import lucuma.core.model.ImageQuality
import lucuma.core.model.Observation
import lucuma.core.model.User
import lucuma.core.model.sequence.Step
import monocle.Lens
import monocle.Optional
import monocle.syntax.all.focus
import mouse.all.*
import observe.model.*
import observe.model.config.*
import observe.model.enums.BatchExecState
import observe.model.enums.Resource
import observe.model.enums.RunOverride
import observe.server.SequenceGen.AtomGen
import observe.server.engine.EngineStep
import observe.server.engine.Event
import observe.server.engine.Handle.given
import observe.server.engine.{EngineStep as _, *}
import observe.server.events.*
import observe.server.odb.OdbProxy
import org.typelevel.log4cats.Logger

import scala.annotation.unused
import scala.concurrent.duration.*

import SeqEvent.*

trait ObserveEngine[F[_]] {

  val systems: Systems[F]

  def start(
    obsId:       Observation.Id,
    user:        User,
    observer:    Observer,
    clientId:    ClientId,
    runOverride: RunOverride
  ): F[Unit]

  def loadNextAtom(
    obsId:    Observation.Id,
    user:     User,
    observer: Observer,
    atomType: SequenceType
  ): F[Unit]

  def requestPause(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User
  ): F[Unit]

  def requestCancelPause(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User
  ): F[Unit]

  def setBreakpoints(
    obsId:    Observation.Id,
    user:     User,
    observer: Observer,
    stepId:   Set[Step.Id],
    v:        Breakpoint
  ): F[Unit]

  def setOperator(user: User, name: Operator): F[Unit]

  def setObserver(
    obsId: Observation.Id,
    user:  User,
    name:  Observer
  ): F[Unit]

  // Systems overrides
  def setTcsEnabled(
    obsId:    Observation.Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit]

  def setGcalEnabled(
    obsId:    Observation.Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit]

  def setInstrumentEnabled(
    obsId:    Observation.Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit]

  def setDhsEnabled(
    obsId:    Observation.Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit]

  def selectSequence(
    i:        Instrument,
    obsId:    Observation.Id,
    observer: Observer,
    user:     User,
    clientId: ClientId
  ): F[Unit]

  def clearLoadedSequences(user: User): F[Unit]

  def resetConditions: F[Unit]

  def setConditions(conditions: Conditions, user: User): F[Unit]

  def setImageQuality(iq: ImageQuality, user: User, clientId: ClientId): F[Unit]

  def setWaterVapor(wv: WaterVapor, user: User, clientId: ClientId): F[Unit]

  def setSkyBackground(sb: SkyBackground, user: User, clientId: ClientId): F[Unit]

  def setCloudExtinction(cc: CloudExtinction, user: User, clientId: ClientId): F[Unit]

  def requestRefresh(clientId: ClientId): F[Unit]

  def stopObserve(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User,
    graceful: Boolean
  ): F[Unit]

  def abortObserve(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User
  ): F[Unit]

  def pauseObserve(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User,
    graceful: Boolean
  ): F[Unit]

  def resumeObserve(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User
  ): F[Unit]

  def addSequencesToQueue(qid: QueueId, obsIds: List[Observation.Id]): F[Unit]

  def addSequenceToQueue(qid: QueueId, obsId: Observation.Id): F[Unit]

  def removeSequenceFromQueue(qid: QueueId, obsId: Observation.Id): F[Unit]

  def moveSequenceInQueue(
    qid:   QueueId,
    obsId: Observation.Id,
    delta: Int,
    cid:   ClientId
  ): F[Unit]

  def clearQueue(qid: QueueId): F[Unit]

  def startQueue(
    qid:      QueueId,
    observer: Observer,
    user:     User,
    clientId: ClientId
  ): F[Unit]

  def stopQueue(qid: QueueId, clientId: ClientId): F[Unit]

  /**
   * Triggers the application of a specific step configuration to a system
   */
  def configSystem(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User,
    stepId:   Step.Id,
    sys:      Resource | Instrument,
    clientID: ClientId
  ): F[Unit]

  def clientEventStream: Stream[F, TargetedClientEvent]

  // Used by tests
  private[server] def stream(
    s0: EngineState[F]
  ): Stream[F, (EventResult, EngineState[F])]

  private[server] def loadSequenceEndo(
    observer: Option[Observer],
    seqg:     SequenceGen[F],
    l:        Lens[EngineState[F], Option[SequenceData[F]]],
    cleanup:  F[Unit]
  ): Endo[EngineState[F]] = ODBSequencesLoader.loadSequenceEndo(observer, seqg, l, cleanup)
}

object ObserveEngine {

  def createTranslator[F[_]: Async: Logger](
    site:          Site,
    systems:       Systems[F],
    conditionsRef: Ref[F, Conditions]
  ): F[SeqTranslate[F]] =
    SeqTranslate(site, systems, conditionsRef)

  private def observations[F[_]](st: EngineState[F]): List[SequenceData[F]] =
    List(st.selected.gmosSouth, st.selected.gmosNorth, st.selected.flamingos2).flattenOption

  private def systemsBeingConfigured[F[_]](st: EngineState[F]): Set[Resource | Instrument] =
    observations(st)
      .filter(d => d.seq.status.isError || d.seq.status.isIdle)
      .flatMap(s =>
        s.seq.getSingleActionStates
          .filter(_._2.started)
          .keys
          .toList
          .mapFilter(s.seqGen.resourceAtCoords)
      )
      .toSet

  /**
   * Resource in use = Resources used by running sequences, plus the systems that are being
   * configured because a user commanded a manual configuration apply.
   */
  def resourcesInUse[F[_]](st: EngineState[F]): Set[Resource | Instrument] =
    observations(st)
      .mapFilter(s => s.seq.status.isRunning.option(s.seqGen.resources))
      .foldK ++
      systemsBeingConfigured(st)

  /**
   * Resources reserved by running queues.
   */
  def resourcesReserved[F[_]](st: EngineState[F]): Set[Resource | Instrument] = {
    def reserved(q: ExecutionQueue): Set[Resource | Instrument] = q.queue.collect {
      case s if !s.state.isCompleted => s.resources
    }.foldK

    val runningQs = st.queues.values.filter(_.status.running)

    runningQs.map(reserved).toList.foldK

  }

  /**
   * Creates a stream that will follow a heartbeat and raise an error if the heartbeat doesn't get
   * emitted for timeout
   *
   * Credit: Fabio Labella
   * https://gitter.im/functional-streams-for-scala/fs2?at=5e0a6efbfd580457e79aaf0a
   */
  def failIfNoEmitsWithin[F[_]: Async, A](
    timeout: FiniteDuration,
    msg:     String
  ): Pipe[F, A, A] = in => {
    import scala.concurrent.TimeoutException
    def now = Temporal[F].realTime

    Stream.eval(now.flatMap(Ref[F].of)).flatMap { lastActivityAt =>
      in.evalTap(_ => now.flatMap(lastActivityAt.set))
        .concurrently {
          Stream.repeatEval {
            (now, lastActivityAt.get)
              .mapN(_ - _)
              .flatMap { elapsed =>
                val t = timeout - elapsed

                Sync[F]
                  .raiseError[Unit](new TimeoutException(msg))
                  .whenA(t <= 0.nanos) >> Temporal[F].sleep(t)
              }
          }
        }
    }
  }

  /**
   * Find the observations in an execution queue that would be run next, taking into account the
   * resources required by each observation and the resources currently in use. The order in the
   * queue defines the priority of the observations. Failed or stopped sequences in the queue keep
   * their instruments taken, preventing that the queue starts other sequences for those
   * instruments.
   * @param qid
   *   The execution queue id
   * @param st
   *   The current engine state
   * @return
   *   The set of all observations in the execution queue `qid` that can be started to run in
   *   parallel.
   */
  def findRunnableObservations[F[_]](qid: QueueId)(st: EngineState[F]): Set[Observation.Id] = {
    // Set of all resources in use
    val used = resourcesInUse(st)
    // For each observation in the queue that is not yet run, retrieve the required resources
    val obs  = st.queues
      .get(qid)
      .map(_.queue.collect {
        case s if !s.state.isRunning && !s.state.isCompleted =>
          s.obsId -> s.resources
      })
      .orEmpty

    obs
      .foldLeft((used, Set.empty[Observation.Id])) { case ((u, a), (oid, res)) =>
        if (u.intersect(res).isEmpty)
          (u ++ res, a + oid)
        else (u, a)
      }
      ._2
  }

  /**
   * Find next runnable observations given that a set of resources has just being released
   * @param qid
   *   The execution queue id
   * @param st
   *   The current engine state
   * @param freed
   *   Resources that were freed
   * @return
   *   The set of all observations in the execution queue `qid` that can be started to run in
   *   parallel.
   */
  @unused
  private def nextRunnableObservations[F[_]](qid: QueueId, freed: Set[Resource | Instrument])(
    st: EngineState[F]
  ): Set[Observation.Id] = {
    // Set of all resources in use
    val used = resourcesInUse(st)
    // For each observation in the queue that is not yet run, retrieve the required resources
    val obs  = st.queues
      .get(qid)
      .map(_.queue.collect {
        case s if !s.state.isRunning && !s.state.isCompleted =>
          s.obsId -> s.resources
      })
      .orEmpty

    // Calculate instruments reserved by failed sequences in the queue
    val resFailed: Set[Instrument] = st.queues
      .get(qid)
      .map(
        _.queue.mapFilter(s => s.state.isError.option(s.instrument))
      )
      .orEmpty
      .toSet

    obs
      .foldLeft((used ++ resFailed, Set[Observation.Id]())) { case ((u, a), (oid, res)) =>
        if (u.intersect(res).isEmpty && freed.intersect(res).nonEmpty) (u ++ res, a + oid)
        else (u, a)
      }
      ._2
  }

  /**
   * shouldSchedule checks if a set of sequences are candidates for been run in a queue. It is used
   * to check if sequences added to a queue should be started.
   */
  @annotation.unused
  private def shouldSchedule[F[_]](qid: QueueId, sids: Set[Observation.Id])(
    st: EngineState[F]
  ): Set[Observation.Id] =
    findRunnableObservations(qid)(st).intersect(sids)

  private def onAtomComplete[F[_]: MonadCancelThrow](
    odb:           OdbProxy[F],
    translator:    SeqTranslate[F]
  )(
    executeEngine: Engine[F],
    obsId:         Observation.Id
  ): EngineHandle[F, SeqEvent] =
    Handle
      .getState[F, EngineState[F], Event[F]]
      .map(EngineState.atSequence[F](obsId).getOption)
      .flatMap {
        _.map { seq =>
          seq.seqGen.nextAtom.sequenceType match {
            case SequenceType.Acquisition =>
              Handle.pure[F, EngineState[F], Event[F], SeqEvent](
                SeqEvent.AtomCompleted(
                  obsId,
                  SequenceType.Acquisition,
                  seq.seqGen.nextAtom.atomId
                )
              )
            case SequenceType.Science     =>
              tryNewAtom[F](odb, translator, executeEngine, obsId, SequenceType.Science)
                .as(
                  SeqEvent.AtomCompleted(obsId, SequenceType.Science, seq.seqGen.nextAtom.atomId)
                )
          }
        }.getOrElse(
          EngineHandle.pure[F, SeqEvent](NullSeqEvent)
        )
      }

  private def updateAtom[F[_]](
    obsId: Observation.Id,
    atm:   Option[AtomGen[F]] // May be None if the sequence is completed
  ): Endo[EngineState[F]] =
    (st: EngineState[F]) =>
      EngineState
        .atSequence[F](obsId)
        .modify { (seqData: SequenceData[F]) =>
          val newSeqData: SequenceData[F] = // Replace nextAtom
            atm.fold(seqData): a =>
              SequenceData.seqGen.modify(SequenceGen.replaceNextAtom(a))(seqData)

          newSeqData
            .focus(_.seq)
            .modify(s => // Initialize the sequence state
              val steps: List[EngineStep[F]] =
                toStepList(
                  newSeqData.seqGen,
                  newSeqData.overrides,
                  HeaderExtraData(st.conditions, st.operator, newSeqData.observer)
                ).map(_._1) // Ignore breakpoints

              val newState: Sequence.State[F] =
                Sequence.State.init(
                  atm.fold(Sequence.empty[F](obsId)) { a =>
                    Sequence.sequence[F](obsId, a.atomId, steps, s.breakpoints)
                  }
                )

              // Revive sequence if it was completed - or complete if no more steps
              val newSeqState: SequenceState =
                if s.status.isCompleted && atm.nonEmpty then SequenceState.Idle
                else if atm.isEmpty then SequenceState.Completed
                else s.status

              Sequence.State.status.replace(newSeqState)(newState)
            )
        }(st)

  def tryNewAtom[F[_]: MonadCancelThrow](
    odb:           OdbProxy[F],
    translator:    SeqTranslate[F],
    executeEngine: Engine[F],
    obsId:         Observation.Id,
    atomType:      SequenceType
  ): EngineHandle[F, Unit] =
    EngineHandle.fromSingleEventF(
      odb.read(obsId).map { odbObsData =>
        translator
          .nextAtom(odbObsData, atomType)
          ._2
          .map { atm =>
            Event.modifyState[F](
              EngineHandle
                .modifyState_ { (st: EngineState[F]) =>
                  updateAtom(obsId, atm.some)(st)
                }
                .flatMap(_ =>
                  executeEngine.startNewAtom(obsId) *>
                    EngineHandle.pure:
                      SeqEvent.NewAtomLoaded(obsId, atm.sequenceType, atm.atomId)
                )
            )
          }
          .getOrElse(
            Event.modifyState[F](
              executeEngine.startNewAtom(obsId).as(SeqEvent.NoMoreAtoms(obsId))
            )
          )
      }
    )

  def onAtomReload[F[_]: MonadCancelThrow: Logger](
    odb:           OdbProxy[F],
    translator:    SeqTranslate[F]
  )(
    executeEngine: Engine[F],
    obsId:         Observation.Id,
    reloadReason:  ReloadReason
  ): EngineHandle[F, SeqEvent] =
    EngineHandle.getState
      .map(EngineState.atSequence[F](obsId).getOption)
      .flatMap {
        _.map { seq =>
          tryAtomReload[F](
            odb,
            translator,
            executeEngine,
            obsId,
            seq.seqGen.nextAtom.sequenceType,
            reloadReason
          )
            .as(SeqEvent.NullSeqEvent)
        }.getOrElse(
          EngineHandle.pure[F, SeqEvent](NullSeqEvent)
        )
      }

  private def tryAtomReload[F[_]: MonadCancelThrow: Logger](
    odb:           OdbProxy[F],
    translator:    SeqTranslate[F],
    executeEngine: Engine[F],
    obsId:         Observation.Id,
    atomType:      SequenceType,
    reloadReason:  ReloadReason
  ): EngineHandle[F, Unit] =
    EngineHandle
      .getSequenceState(obsId)
      .flatMap: seqState =>
        // In case of an edit event, we only reload if the sequence isn't running.
        val shouldUpdate: Boolean =
          reloadReason == ReloadReason.SequenceFlow || !seqState.forall(_.status.isRunning)

        if shouldUpdate then {
          // This must be done within an event, so that the results are sent to the clients.
          EngineHandle.fromSingleEvent:
            Event.modifyState[F]:
              (for
                _          <- EngineHandle.debug(s"Reloading atom for observation [$obsId]")
                odbObsData <- EngineHandle.liftF(odb.read(obsId))
                atomGen    <-
                  EngineHandle.modifyState: (oldState: EngineState[F]) =>
                    val atomGen: Option[AtomGen[F]] = translator.nextAtom(odbObsData, atomType)._2
                    val newState: EngineState[F]    = updateAtom(obsId, atomGen)(oldState)
                    (newState, atomGen)
                continue   <-
                  atomGen.fold(
                    EngineHandle.fromSingleEvent(Event.finished(obsId)).as(SeqEvent.NullSeqEvent)
                  ): atm =>
                    if reloadReason == ReloadReason.SequenceFlow then
                      executeEngine.startNewAtom(obsId).as(SeqEvent.NullSeqEvent)
                    else
                      Handle.pure:
                        SeqEvent.NewAtomLoaded(obsId, atm.sequenceType, atm.atomId)
              yield continue)
                .handleErrorWith: e =>
                  EngineHandle.logError(e)(s"Error reloading atom for observation [$obsId]") >>
                    EngineHandle
                      .fromSingleEvent(
                        Event.failed(
                          obsId,
                          0,
                          Result.Error(s"Error updating sequence, cannot continue: ${e.getMessage}")
                        )
                      )
                      .as(SeqEvent.NullSeqEvent)
        } else
          EngineHandle.debug:
            s"Edit event for observation [$obsId] received while running, ignoring."

  /**
   * Build Observe and setup epics
   */
  def build[F[_]: Async: Logger](
    site:    Site,
    systems: Systems[F],
    conf:    ObserveEngineConfiguration
  )(using Monoid[F[Unit]]): F[ObserveEngine[F]] = for {
    rc  <- Ref.of[F, Conditions](Conditions.Default)
    tr  <- createTranslator(site, systems, rc)
    eng <- Engine.build[F](
             onAtomComplete[F](systems.odb, tr),
             onAtomReload[F](systems.odb, tr)
           )
  } yield new ObserveEngineImpl[F](eng, systems, conf, tr, rc)
}
