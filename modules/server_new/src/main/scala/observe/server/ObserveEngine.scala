// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Applicative
import cats.Endo
import cats.Functor
import cats.Monad
import cats.MonoidK
import cats.data.NonEmptyList
import cats.effect.Async
import cats.effect.Ref
import cats.effect.Temporal
import cats.effect.kernel.Sync
import cats.syntax.all.*
import eu.timepit.refined.types.numeric.NonNegShort
import fs2.Pipe
import fs2.Stream
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.enums.Site
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.ConstraintSet
import lucuma.core.model.Observation
import lucuma.core.model.User
import lucuma.core.model.sequence.Step
import lucuma.core.model.sequence.StepConfig as OcsStepConfig
import monocle.Focus
import monocle.Lens
import monocle.Optional
import monocle.function.Index.mapIndex
import monocle.syntax.all.focus
import mouse.all.*
import observe.engine
import observe.engine.EventResult.*
import observe.engine.Handle.given
import observe.engine.Result.Partial
import observe.engine.{EngineStep as _, *}
import observe.model.*
import observe.model.ObservationProgress
import observe.model.UserPrompt.Discrepancy
import observe.model.UserPrompt.ObsConditionsCheckOverride
import observe.model.UserPrompt.SeqCheck
import observe.model.UserPrompt.TargetCheckOverride
import observe.model.config.*
import observe.model.enums.BatchExecState
import observe.model.enums.PendingObserveCmd
import observe.model.enums.PendingObserveCmd.*
import observe.model.enums.Resource
import observe.model.enums.RunOverride
import observe.model.events.*
import observe.server.SequenceGen.AtomGen
import observe.server.events.*
import observe.server.odb.OdbProxy
import org.typelevel.log4cats.Logger

import java.util.concurrent.TimeUnit
import scala.annotation.unused
import scala.collection.immutable.SortedMap
import scala.concurrent.duration.*

import SeqEvent.*
import ClientEvent.*

trait ObserveEngine[F[_]] {

  val systems: Systems[F]

  def start(
    id:          Observation.Id,
    user:        User,
    observer:    Observer,
    clientId:    ClientId,
    runOverride: RunOverride
  ): F[Unit]

  def loadNextAtom(
    id:       Observation.Id,
    user:     User,
    observer: Observer,
    atomType: SequenceType
  ): F[Unit]

  def requestPause(
    id:       Observation.Id,
    observer: Observer,
    user:     User
  ): F[Unit]

  def requestCancelPause(
    id:       Observation.Id,
    observer: Observer,
    user:     User
  ): F[Unit]

  def setBreakpoints(
    seqId:    Observation.Id,
    user:     User,
    observer: Observer,
    stepId:   List[Step.Id],
    v:        Breakpoint
  ): F[Unit]

  def setOperator(user: User, name: Operator): F[Unit]

  def setObserver(
    seqId: Observation.Id,
    user:  User,
    name:  Observer
  ): F[Unit]

  // Systems overrides
  def setTcsEnabled(
    seqId:    Observation.Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit]

  def setGcalEnabled(
    seqId:    Observation.Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit]

  def setInstrumentEnabled(
    seqId:    Observation.Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit]

  def setDhsEnabled(
    seqId:    Observation.Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit]

  def selectSequence(
    i:        Instrument,
    sid:      Observation.Id,
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
    seqId:    Observation.Id,
    observer: Observer,
    user:     User,
    graceful: Boolean
  ): F[Unit]

  def abortObserve(
    seqId:    Observation.Id,
    observer: Observer,
    user:     User
  ): F[Unit]

  def pauseObserve(
    seqId:    Observation.Id,
    observer: Observer,
    user:     User,
    graceful: Boolean
  ): F[Unit]

  def resumeObserve(
    seqId:    Observation.Id,
    observer: Observer,
    user:     User
  ): F[Unit]

  def addSequencesToQueue(qid: QueueId, seqIds: List[Observation.Id]): F[Unit]

  def addSequenceToQueue(qid: QueueId, seqId: Observation.Id): F[Unit]

  def removeSequenceFromQueue(qid: QueueId, seqId: Observation.Id): F[Unit]

  def moveSequenceInQueue(
    qid:   QueueId,
    seqId: Observation.Id,
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
    sid:      Observation.Id,
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
  ): Stream[F, (EventResult[SeqEvent], EngineState[F])]

  private[server] def loadSequenceEndo(
    observer: Option[Observer],
    seqg:     SequenceGen[F],
    l:        Lens[EngineState[F], Option[SequenceData[F]]]
  ): Endo[EngineState[F]] = ODBSequencesLoader.loadSequenceEndo(observer, seqg, l)
}

object ObserveEngine {

  private class ObserveEngineImpl[F[_]: Async: Logger](
    executeEngine:                    Engine[F, EngineState[F], SeqEvent],
    override val systems:             Systems[F],
    @annotation.unused settings:      ObserveEngineConfiguration,
    translator:                       SeqTranslate[F],
    @annotation.unused conditionsRef: Ref[F, Conditions]
  ) extends ObserveEngine[F] {

    /**
     * Check if the resources to run a sequence are available
     * @return
     *   true if resources are available
     */
    private def checkResources(seqId: Observation.Id)(st: EngineState[F]): Boolean = {
      // Resources used by running sequences
      val used = resourcesInUse(st)

      // Resources that will be used by sequences in running queues
      val reservedByQueues = resourcesReserved(st)

      st.sequences
        .get(seqId)
        .exists(x =>
          x.seqGen.resources.intersect(used).isEmpty && (
            st.queues.values.filter(_.status(st).running).exists(_.queue.contains(seqId)) ||
              x.seqGen.resources.intersect(reservedByQueues).isEmpty
          )
        )
    }

    // Starting step is either the one given, or the first one not run
    private def findStartingStep(
      obs:    SequenceData[F],
      stepId: Option[Step.Id]
    ): Option[SequenceGen.StepGen[F]] = for {
      stp    <- stepId.orElse(obs.seq.currentStep.map(_.id))
      stpGen <- obs.seqGen.nextAtom.steps.find(_.id === stp)
    } yield stpGen

    private def findFirstCheckRequiredStep(
      obs:    SequenceData[F],
      stepId: Step.Id
    ): Option[SequenceGen.StepGen[F]] =
      obs.seqGen.nextAtom.steps.dropWhile(_.id =!= stepId).find(a => stepRequiresChecks(a.config))

    /**
     * Check if the target on the TCS matches the Observe target
     * @return
     *   an F that returns an optional TargetMatchResult if the targets don't match
     */
    private def sequenceTcsTargetMatch(
      seqData: SequenceData[F]
    ): F[Option[TargetCheckOverride]] =
      seqData.seqGen.obsData.targetEnvironment.firstScienceTarget
        .map(_.targetName.toString)
        .map { seqTarget =>
          systems.tcsKeywordReader.sourceATarget.objectName.map { tcsTarget =>
            (seqTarget =!= tcsTarget).option(
              TargetCheckOverride(UserPrompt.Discrepancy(tcsTarget, seqTarget))
            )
          }
        }
        .getOrElse(none.pure[F])

    private def stepRequiresChecks(stepConfig: OcsStepConfig): Boolean = stepConfig match {
      case OcsStepConfig.Gcal(_, _, _, _) => true
      case OcsStepConfig.Science          => true
      case OcsStepConfig.SmartGcal(_)     => true
      case _                              => false
    }

    private def checkCloudCover(
      actual:    Option[CloudExtinction],
      requested: CloudExtinction
    ): Boolean =
      actual.forall(_ <= requested)

    private def checkImageQuality(
      actual:    Option[ImageQuality],
      requested: ImageQuality
    ): Boolean =
      actual.forall(_ <= requested)

    private def checkSkyBackground(
      actual:    Option[SkyBackground],
      requested: SkyBackground
    ): Boolean =
      actual.forall(_ <= requested)

    private def checkWaterVapor(actual: Option[WaterVapor], requested: WaterVapor): Boolean =
      actual.forall(_ <= requested)

    private def observingConditionsMatch(
      actualObsConditions:   Conditions,
      requiredObsConditions: ConstraintSet
    ): Option[ObsConditionsCheckOverride] = {

      val UnknownStr: String = "Unknown"
      val reqCE              = requiredObsConditions.cloudExtinction
      val reqIQ              = requiredObsConditions.imageQuality
      val reqSB              = requiredObsConditions.skyBackground
      val reqWV              = requiredObsConditions.waterVapor

      val ccCmp = (!checkCloudCover(actualObsConditions.ce, reqCE))
        .option(Discrepancy(actualObsConditions.ce.fold(UnknownStr)(_.label), reqCE.label))

      val iqCmp = (!checkImageQuality(actualObsConditions.iq, reqIQ))
        .option(Discrepancy(actualObsConditions.iq.fold(UnknownStr)(_.label), reqIQ.label))

      val sbCmp = (!checkSkyBackground(actualObsConditions.sb, reqSB))
        .option(Discrepancy(actualObsConditions.sb.fold(UnknownStr)(_.label), reqSB.label))

      val wvCmp = (!checkWaterVapor(actualObsConditions.wv, reqWV))
        .option(Discrepancy(actualObsConditions.wv.fold(UnknownStr)(_.label), reqWV.label))

      (ccCmp.nonEmpty || iqCmp.nonEmpty || sbCmp.nonEmpty || wvCmp.nonEmpty)
        .option(ObsConditionsCheckOverride(ccCmp, iqCmp, sbCmp, wvCmp))

    }

    private def clearObsCmd(id: Observation.Id): HandlerType[F, SeqEvent] = { (s: EngineState[F]) =>
      (EngineState
         .atSequence[F](id)
         .andThen(SequenceData.pendingObsCmd)
         .replace(None)(s),
       SeqEvent.NullSeqEvent: SeqEvent
      )
    }.toHandle

    private def setObsCmd(id: Observation.Id, cmd: PendingObserveCmd): HandlerType[F, SeqEvent] = {
      (s: EngineState[F]) =>
        (EngineState
           .atSequence[F](id)
           .andThen(SequenceData.pendingObsCmd)
           .replace(cmd.some)(s),
         SeqEvent.NullSeqEvent: SeqEvent
        )
    }.toHandle

    // Produce a Handle that will send a SequenceStart notification to the ODB, and produces the (sequenceId, stepId)
    // if there is a valid sequence with a valid current step.
    private def sequenceStart(
      obsId: Observation.Id
    ): HandlerType[F, Option[(Observation.Id, Step.Id)]] =
      executeEngine.get.flatMap { s =>
        EngineState
          .atSequence(obsId)
          .getOption(s)
          .flatMap { seq =>
            val startVisit: HandlerType[F, SeqEvent] = if (!seq.visitStartDone) {
              Handle
                .fromStream[F, EngineState[F], EventType[F]](
                  Stream.eval[F, EventType[F]](
                    systems.odb
                      .visitStart(obsId, seq.seqGen.staticCfg)
                      .as(
                        Event.modifyState(
                          executeEngine
                            .modify {
                              EngineState.atSequence[F](obsId).modify(_.withCompleteVisitStart)
                            }
                            .as(SeqEvent.NullSeqEvent)
                        )
                      )
                  )
                )
                .as(SeqEvent.NullSeqEvent)
            } else
              Handle.pure(SeqEvent.NullSeqEvent)

            val startAtom: HandlerType[F, SeqEvent] = if (!seq.atomStartDone) {
              Handle
                .fromStream[F, EngineState[F], EventType[F]](
                  Stream.eval[F, EventType[F]](
                    systems.odb
                      .atomStart(
                        obsId,
                        seq.seqGen.instrument,
                        seq.seqGen.nextAtom.sequenceType,
                        NonNegShort.unsafeFrom(seq.seqGen.nextAtom.steps.length.toShort),
                        seq.seqGen.nextAtom.atomId.some
                      )
                      .as(
                        Event.modifyState(
                          executeEngine
                            .modify {
                              EngineState
                                .atSequence(obsId)
                                .modify(_.withCompleteAtomStart)
                            }
                            .as(SeqEvent.NullSeqEvent)
                        )
                      )
                  )
                )
                .as(SeqEvent.NullSeqEvent)
            } else
              Handle.pure(SeqEvent.NullSeqEvent)

            seq.seq.currentStep.map { curStep =>
              (
                startVisit *>
                  Handle
                    .fromStream[F, EngineState[F], EventType[F]](
                      Stream.eval[F, EventType[F]](
                        systems.odb
                          .sequenceStart(obsId)
                          .as(Event.nullEvent)
                      )
                    ) *>
                  startAtom
              ).as((obsId, curStep.id).some)
            }
          }
          .getOrElse(executeEngine.pure(none[(Observation.Id, Step.Id)]))
      }

    private def startAfterCheck(
      startAction: HandlerType[F, Unit],
      id:          Observation.Id
    ): HandlerType[F, SeqEvent] =
      startAction.reversedStreamFlatMap(_ =>
        sequenceStart(id).map(
          _.map { case (sid, stepId) => SequenceStart(sid, stepId) }.getOrElse(NullSeqEvent)
        )
      )

    private def startChecks(
      startAction: HandlerType[F, Unit],
      id:          Observation.Id,
      clientId:    ClientId,
      stepId:      Option[Step.Id],
      runOverride: RunOverride
    ): HandlerType[F, SeqEvent] =
      executeEngine.get.flatMap { st =>
        EngineState
          .atSequence(id)
          .getOption(st)
          .map { seq =>
            executeEngine
              .liftF {
                (for {
                  ststp <- findStartingStep(seq, stepId)
                  sp    <- findFirstCheckRequiredStep(seq, ststp.id)
                } yield sequenceTcsTargetMatch(seq).map { tchk =>
                  (ststp.some,
                   List(tchk,
                        observingConditionsMatch(st.conditions, seq.seqGen.obsData.constraintSet)
                   )
                     .collect { case Some(x) => x }
                     .widen[SeqCheck]
                  )
                })
                  .getOrElse((none[SequenceGen.StepGen[F]], List.empty[SeqCheck]).pure[F])
              }
              .flatMap { case (stpg, checks) =>
                (checkResources(id)(st), stpg, checks, runOverride) match {
                  // Resource check fails
                  case (false, _, _, _)                             =>
                    executeEngine.unit.as[SeqEvent](
                      Busy(id, clientId)
                    )
                  // Target check fails and no override
                  case (_, Some(stp), x :: xs, RunOverride.Default) =>
                    executeEngine.unit.as[SeqEvent](
                      RequestConfirmation(
                        UserPrompt.ChecksOverride(id, stp.id, NonEmptyList(x, xs)),
                        clientId
                      )
                    )
                  // Allowed to run
                  case _                                            => startAfterCheck(startAction, id)
                }
              }
          }
          .getOrElse(
            executeEngine.unit.as[SeqEvent](NullSeqEvent)
          ) // Trying to run a sequence that does not exists. This should never happen.
      }

    // Stars a sequence from the first non executed step. The method checks for resources conflict.
    override def start(
      id:          Observation.Id,
      user:        User,
      observer:    Observer,
      clientId:    ClientId,
      runOverride: RunOverride
    ): F[Unit] = executeEngine.offer(
      Event.modifyState[F, EngineState[F], SeqEvent](
        setObserver(id, observer) *>
          clearObsCmd(id) *>
          startChecks(executeEngine.start(id), id, clientId, none, runOverride)
      )
    )

    override def loadNextAtom(
      id:       Observation.Id,
      user:     User,
      observer: Observer,
      atomType: SequenceType
    ): F[Unit] = setObserver(id, user, observer) *>
      executeEngine.offer(
        Event.modifyState[F, EngineState[F], SeqEvent](
          clearObsCmd(id) *>
            userNextAtom(id, atomType).as(SeqEvent.NullSeqEvent)
        )
      )

    def userNextAtom(
      id:       Observation.Id,
      atomType: SequenceType
    ): HandlerType[F, Unit] = executeEngine.get.flatMap { st =>
      EngineState
        .atSequence(id)
        .getOption(st)
        .flatMap { seq =>
          seq.seq.pending.isEmpty.option(
            tryNewAtom(systems.odb, translator, executeEngine, id, atomType)
          )
        }
        .getOrElse(executeEngine.unit)
    }

    override def requestPause(
      seqId:    Observation.Id,
      observer: Observer,
      user:     User
    ): F[Unit] = setObserver(seqId, user, observer) *>
      executeEngine.offer(Event.pause[F, EngineState[F], SeqEvent](seqId, user))

    override def requestCancelPause(
      seqId:    Observation.Id,
      observer: Observer,
      user:     User
    ): F[Unit] = setObserver(seqId, user, observer) *>
      executeEngine.offer(Event.cancelPause[F, EngineState[F], SeqEvent](seqId, user))

    override def setBreakpoints(
      seqId:    Observation.Id,
      user:     User,
      observer: Observer,
      steps:    List[Step.Id],
      v:        Breakpoint
    ): F[Unit] =
      // Set the observer after the breakpoints are set to do optimistic updates on the UI
      executeEngine.offer(Event.breakpoints[F, EngineState[F], SeqEvent](seqId, user, steps, v)) *>
        setObserver(seqId, user, observer)

    override def setOperator(user: User, name: Operator): F[Unit] =
      logDebugEvent(s"ObserveEngine: Setting Operator name to '$name' by ${user.displayName}") *>
        executeEngine.offer(
          Event.modifyState[F, EngineState[F], SeqEvent](
            (EngineState.operator[F].replace(name.some) >>> refreshSequences)
              .withEvent(SetOperator(name, user.some))
              .toHandle
          )
        )

    private def setObserver(
      id:       Observation.Id,
      observer: Observer,
      event:    SeqEvent = SeqEvent.NullSeqEvent
    ): HandlerType[F, SeqEvent] = { (s: EngineState[F]) =>
      (EngineState
         .atSequence[F](id)
         .andThen(SequenceData.observer)
         .replace(observer.some)(s),
       event
      )
    }.toHandle

    override def setObserver(
      seqId: Observation.Id,
      user:  User,
      name:  Observer
    ): F[Unit] = logDebugEvent(
      s"ObserveEngine: Setting Observer name to '$name' for sequence '$seqId' by ${user.displayName}"
    ) *>
      executeEngine.offer(
        Event.modifyState[F, EngineState[F], SeqEvent](
          (EngineState
            .atSequence(seqId)
            .modify(Focus[SequenceData[F]](_.observer).replace(name.some)) >>>
            refreshSequence(seqId)).withEvent(SetObserver(seqId, user.some, name)).toHandle
        )
      )

//    private def selectSequenceEvent(
//      i:        Instrument,
//      sid:      Observation.Id,
//      observer: Observer,
//      user:     User,
//      clientId: ClientId
//    ): EventType[F] = {
//      val lens = EngineState
//        .sequences[F]
//        .andThen(mapIndex[Observation.Id, SequenceData[F]].index(sid))
//        .modify(Focus[SequenceData](_.observer).replace(observer.some)) >>>
//        EngineState.instrumentLoadedL[F](i).replace(sid.some) >>>
//        refreshSequence(sid)
//      def testRunning(st: EngineState[F]): Boolean = (for {
//        sels   <- st.selected.get(i)
//        obsseq <- st.sequences.get(sels)
//      } yield obsseq.seq.status.isRunning).getOrElse(false)
//
//      Event.modifyState[F, EngineState[F], SeqEvent] {
//        ((st: EngineState[F]) => {
//          if (!testRunning(st)) lens.withEvent(AddLoadedSequence(i, sid, user, clientId))(st)
//          else (st, NotifyUser(InstrumentInUse(sid, i), clientId))
//        }).toHandle
//      }
//    }

    override def selectSequence(
      i:        Instrument,
      sid:      Observation.Id,
      observer: Observer,
      user:     User,
      clientId: ClientId
    ): F[Unit] =
      val author = s", by '${user.displayName}' on client ${clientId.value}."
      executeEngine.inject(
        systems.odb
          .read(sid)
          .flatMap(translator.sequence)
          .attempt
          .flatMap(
            _.fold(
              e =>
                Logger[F]
                  .warn(e)(s"Error loading observation $sid$author")
                  .as(
                    Event.pure(
                      SeqEvent.NotifyUser(
                        Notification.RequestFailed(
                          List(
                            s"Error loading observation $sid",
                            e.getMessage
                          )
                        ),
                        clientId
                      )
                    )
                  ),
              {
                case (err, None)       =>
                  Logger[F]
                    .warn(
                      err.headOption
                        .map(e => s"Error loading observation $sid: ${e.getMessage}$author")
                        .getOrElse(s"Error loading observation $sid")
                    )
                    .as(
                      Event.pure(
                        SeqEvent.NotifyUser(
                          Notification.RequestFailed(
                            List(
                              s"Error loading observation $sid"
                            ) ++ err.headOption.toList.map(e => e.getMessage)
                          ),
                          clientId
                        )
                      )
                    )
                case (errs, Some(seq)) =>
                  errs.isEmpty
                    .fold(
                      Logger[F].warn(s"Loaded observation $sid$author"),
                      Logger[F]
                        .warn(
                          s"Loaded observation $sid with warnings: ${errs.mkString}$author"
                        )
                    )
                    .as(
                      Event.modifyState[F, EngineState[F], SeqEvent]({ (st: EngineState[F]) =>
                        val l = EngineState
                          .instrumentLoaded[F](seq.instrument)
                        if (l.get(st).forall(s => executeEngine.canUnload(s.seq))) {
                          (
                            st.sequences
                              .get(sid)
                              .fold(
                                ODBSequencesLoader
                                  .loadSequenceEndo(observer.some, seq, l)
                              )(_ => ODBSequencesLoader.reloadSequenceEndo(seq, l))(st),
                            LoadSequence(sid)
                          )
                        } else {
                          (
                            st,
                            SeqEvent.NotifyUser(
                              Notification.RequestFailed(
                                List(
                                  s"Error loading observation $sid",
                                  s"A sequence is running on instrument ${seq.instrument}"
                                )
                              ),
                              clientId
                            )
                          )
                        }
                      }.toHandle)
                    )
              }
            )
          )
      )

    private def logDebugEvent(msg: String): F[Unit] =
      Event.logDebugMsgF[F, EngineState[F], SeqEvent](msg).flatMap(executeEngine.offer)

    private def logDebugEvent(msg: String, user: User, clientId: ClientId): F[Unit] =
      Event
        .logDebugMsgF[F, EngineState[F], SeqEvent](
          s"$msg, by ${user.displayName} from client $clientId"
        )
        .flatMap(executeEngine.offer)

    override def clearLoadedSequences(user: User): F[Unit] =
      logDebugEvent("ObserveEngine: Updating loaded sequences") *>
        executeEngine.offer(
          Event.modifyState[F, EngineState[F], SeqEvent](
            EngineState
              .selected[F]
              .replace(Selected(none, none))
              .withEvent(ClearLoadedSequences(user.some))
              .toHandle
          )
        )

    override def resetConditions: F[Unit] = logDebugEvent("ObserveEngine: Reset conditions") *>
      executeEngine.offer(
        Event.modifyState[F, EngineState[F], SeqEvent](
          (EngineState.conditions[F].replace(Conditions.Default) >>> refreshSequences)
            .withEvent(SetConditions(Conditions.Default, None))
            .toHandle
        )
      )

    override def setConditions(
      conditions: Conditions,
      user:       User
    ): F[Unit] = logDebugEvent("ObserveEngine: Setting conditions") *>
      executeEngine.offer(
        Event.modifyState[F, EngineState[F], SeqEvent](
          (EngineState.conditions[F].replace(conditions) >>> refreshSequences)
            .withEvent(SetConditions(conditions, user.some))
            .toHandle
        )
      )

    override def setImageQuality(iq: ImageQuality, user: User, clientId: ClientId): F[Unit] =
      logDebugEvent(s"ObserveEngine: Setting image quality to $iq", user, clientId) *>
        executeEngine.offer(
          Event.modifyState[F, EngineState[F], SeqEvent](
            (EngineState.conditions[F].andThen(Conditions.iq).replace(iq.some) >>> refreshSequences)
              .withEvent(SetImageQuality(iq, user.some))
              .toHandle
          )
        )

    override def setWaterVapor(wv: WaterVapor, user: User, clientId: ClientId): F[Unit] =
      logDebugEvent(s"ObserveEngine: Setting water vapor to $wv", user, clientId) *>
        executeEngine.offer(
          Event.modifyState[F, EngineState[F], SeqEvent](
            (EngineState.conditions[F].andThen(Conditions.wv).replace(wv.some) >>> refreshSequences)
              .withEvent(SetWaterVapor(wv, user.some))
              .toHandle
          )
        )

    override def setSkyBackground(sb: SkyBackground, user: User, clientId: ClientId): F[Unit] =
      logDebugEvent(s"ObserveEngine: Setting sky background to $sb", user, clientId) *>
        executeEngine.offer(
          Event.modifyState[F, EngineState[F], SeqEvent](
            (EngineState.conditions[F].andThen(Conditions.sb).replace(sb.some) >>> refreshSequences)
              .withEvent(SetSkyBackground(sb, user.some))
              .toHandle
          )
        )

    override def setCloudExtinction(
      ce:       CloudExtinction,
      user:     User,
      clientId: ClientId
    ): F[Unit] = logDebugEvent(s"ObserveEngine: Setting cloud cover to $ce", user, clientId) *>
      executeEngine.offer(
        Event.modifyState[F, EngineState[F], SeqEvent](
          (EngineState.conditions[F].andThen(Conditions.ce).replace(ce.some) >>> refreshSequences)
            .withEvent(SetCloudCover(ce, user.some))
            .toHandle
        )
      )

    override def requestRefresh(clientId: ClientId): F[Unit] =
      executeEngine.offer(Event.poll(clientId))

    private val heartbeatPeriod: FiniteDuration = FiniteDuration(10, TimeUnit.SECONDS)

    private def heartbeatStream: Stream[F, EventType[F]] = {
      // If there is no heartbeat in 5 periods throw an error
      val noHeartbeatDetection =
        ObserveEngine.failIfNoEmitsWithin[F, EventType[F]](
          5 * heartbeatPeriod,
          "Engine heartbeat not detected"
        )
      Stream
        .awakeDelay[F](heartbeatPeriod)
        .as(Event.nullEvent: EventType[F])
        .through(noHeartbeatDetection.andThen(_.recoverWith { case _ =>
          Stream.eval[F, EventType[F]](Event.logErrorMsgF("Observe engine heartbeat undetected"))
        }))
    }

    override def clientEventStream: Stream[F, TargetedClientEvent] =
      Stream.eval(
        executeEngine
          .offer(Event.getState(_ => heartbeatStream.some))
          .as[TargetedClientEvent](BaDum)
      ) ++
        stream(EngineState.default[F])
          .flatMap: x =>
            Stream.eval(notifyODB(x).attempt)
          .flatMap:
            case Right((ev, qState)) => toClientEvent[F](ev, qState, systems.odb)
            // case Left(x)             => Stream.eval(Logger[F].error(x)("Error notifying the ODB").as(NullEvent))
            case Left(_)             => Stream.empty

    override def stream(
      s0: EngineState[F]
    ): Stream[F, (EventResult[SeqEvent], EngineState[F])] =
      executeEngine.process(engineEventsHook)(s0)

    override def stopObserve(
      seqId:    Observation.Id,
      observer: Observer,
      user:     User,
      graceful: Boolean
    ): F[Unit] = setObserver(seqId, user, observer) *>
      executeEngine
        .offer(Event.modifyState[F, EngineState[F], SeqEvent](setObsCmd(seqId, StopGracefully)))
        .whenA(graceful) *>
      executeEngine.offer(
        Event.actionStop[F, EngineState[F], SeqEvent](seqId,
                                                      translator.stopObserve(seqId, graceful)
        )
      )

    override def abortObserve(
      seqId:    Observation.Id,
      observer: Observer,
      user:     User
    ): F[Unit] = setObserver(seqId, user, observer) *>
      executeEngine.offer(
        Event.actionStop[F, EngineState[F], SeqEvent](seqId, translator.abortObserve(seqId))
      )

    override def pauseObserve(
      seqId:    Observation.Id,
      observer: Observer,
      user:     User,
      graceful: Boolean
    ): F[Unit] = setObserver(seqId, user, observer) *>
      executeEngine
        .offer(
          Event.modifyState[F, EngineState[F], SeqEvent](setObsCmd(seqId, PauseGracefully))
        )
        .whenA(graceful) *>
      executeEngine.offer(
        Event.actionStop[F, EngineState[F], SeqEvent](seqId,
                                                      translator.pauseObserve(seqId, graceful)
        )
      )

    override def resumeObserve(
      seqId:    Observation.Id,
      observer: Observer,
      user:     User
    ): F[Unit] =
      executeEngine.offer(Event.modifyState[F, EngineState[F], SeqEvent](clearObsCmd(seqId))) *>
        setObserver(seqId, user, observer) *>
        executeEngine.offer(
          Event.getState[F, EngineState[F], SeqEvent](translator.resumePaused(seqId))
        )

    private def queueO(qid: QueueId): Optional[EngineState[F], ExecutionQueue] =
      Focus[EngineState[F]](_.queues).andThen(mapIndex[QueueId, ExecutionQueue].index(qid))

//    private def cmdStateO(qid: QueueId): Optional[EngineState[F], BatchCommandState] =
//      queueO(qid).andThen(ExecutionQueue.cmdState)

//    private def addSeqs(
//      qid:    QueueId,
//      seqIds: List[Observation.Id]
//    ): HandlerType[F, List[(Observation.Id, ObserveStep.Id)]] =
//      executeEngine.get.flatMap { st =>
//        (
//          for {
//            q    <- st.queues.get(qid)
//            seqs <- seqIds
//                      .filter(sid =>
//                        st.sequences
//                          .get(sid)
//                          .exists(os =>
//                            !os.seq.status.isRunning && !os.seq.status.isCompleted && !q.queue
//                              .contains(sid)
//                          )
//                      )
//                      .some
//                      .filter(_.nonEmpty)
//            if seqs.nonEmpty
//          } yield executeEngine.modify(queueO(qid).modify(_.addSeqs(seqs))) *>
//            ((q.cmdState, q.status(st)) match {
//              case (_, BatchExecState.Completed)       =>
//                (EngineState
//                  .queues[F]
//                  .andThen(mapIndex[QueueId, ExecutionQueue].index(qid))
//                  .andThen(ExecutionQueue.cmdState)
//                  .replace(BatchCommandState.Idle) >>> {
//                  (_, List.empty[(Observation.Id, ObserveStep.Id)])
//                }).toHandle
//              case (BatchCommandState.Run(o, u, c), _) =>
//                executeEngine.get.flatMap(st2 =>
//                  runSequences(shouldSchedule(qid, seqs.toSet)(st2), o, u, c)
//                )
//              case _                                   => executeEngine.pure(List.empty[(Observation.Id, ObserveStep.Id)])
//            })
//        ).getOrElse(executeEngine.pure(List.empty[(Observation.Id, ObserveStep.Id)]))
//      }

    override def addSequencesToQueue(
      qid:    QueueId,
      seqIds: List[Observation.Id]
    ): F[Unit] = Applicative[F].unit
//    executeEngine.offer(
//      Event.modifyState[F, EngineState[F], SeqEvent](
//        addSeqs(qid, seqIds)
//          .as[SeqEvent](UpdateQueueAdd(qid, seqIds))
//      )
//    )
//
    override def addSequenceToQueue(
      qid:   QueueId,
      seqId: Observation.Id
    ): F[Unit] = Applicative[F].unit
//      addSequencesToQueue(q, qid, List(seqId))
//
//    private def removeSeq(
//      qid:   QueueId,
//      seqId: Observation.Id
//    ): HandlerType[F, List[(Observation.Id, ObserveStep.Id)]] =
//      executeEngine.get.flatMap { st =>
//        (
//          for {
//            q    <- st.queues.get(qid)
//            if q.queue.contains(seqId)
//            sstOp = st.sequences.get(seqId).map(_.seq.status)
//            if q.status(st) =!= BatchExecState.Running ||
//              sstOp.forall(sst => !sst.isRunning && !sst.isCompleted)
//          } yield executeEngine.modify(queueO(qid).modify(_.removeSeq(seqId))) *>
//            ((q.cmdState, q.status(st)) match {
//              case (_, BatchExecState.Completed)       =>
//                executeEngine.pure(List.empty[(Observation.Id, ObserveStep.Id)])
//              // If removed sequence was halting the queue, then removing it frees resources to run the next sequences
//              case (BatchCommandState.Run(o, u, c), _) =>
//                shouldSchedule(qid, Set(seqId))(st).isEmpty.fold(
//                  executeEngine.pure(List.empty[(Observation.Id, ObserveStep.Id)]),
//                  st.sequences
//                    .get(seqId)
//                    .map(x => runNextsInQueue(qid, o, u, c, x.seqGen.resources))
//                    .getOrElse(executeEngine.pure(List.empty[(Observation.Id, ObserveStep.Id)]))
//                )
//              case _                                   => executeEngine.pure(List.empty[(Observation.Id, ObserveStep.Id)])
//            })
//        ).getOrElse(executeEngine.pure(List.empty[(Observation.Id, ObserveStep.Id)]))
//      }

    override def removeSequenceFromQueue(
      qid:   QueueId,
      seqId: Observation.Id
    ): F[Unit] = Applicative[F].unit
//    executeEngine.offer(
//      Event.modifyState[F, EngineState[F], SeqEvent](
//        executeEngine.get.flatMap(st =>
//          removeSeq(qid, seqId)
//            .map(
//              UpdateQueueRemove(qid,
//                                List(seqId),
//                                st.queues.get(qid).map(_.queue.indexOf(seqId)).toList,
//                                _
//              )
//            )
//        )
//      )
//    )
//
//    private def moveSeq(qid: QueueId, seqId: Observation.Id, delta: Int): Endo[EngineState[F]] =
//      st =>
//        st.queues
//          .get(qid)
//          .filter(_.queue.contains(seqId))
//          .map { _ =>
//            queueO(qid).modify(_.moveSeq(seqId, delta))(st)
//          }
//          .getOrElse(st)
//
    override def moveSequenceInQueue(
      qid:   QueueId,
      seqId: Observation.Id,
      delta: Int,
      cid:   ClientId
    ): F[Unit] = Applicative[F].unit
//    executeEngine.offer(
//      Event.modifyState[F, EngineState[F], SeqEvent](
//        executeEngine.get.flatMap(_ =>
//          moveSeq(qid, seqId, delta).withEvent(UpdateQueueMoved(qid, cid, seqId, 0)).toHandle
//        )
//      )
//    )
//
    private def clearQ(qid: QueueId): Endo[EngineState[F]] = st =>
      st.queues
        .get(qid)
        .filter(_.status(st) =!= BatchExecState.Running)
        .map { _ =>
          queueO(qid).modify(_.clear)(st)
        }
        .getOrElse(st)

    override def clearQueue(qid: QueueId): F[Unit] = executeEngine.offer(
      Event.modifyState[F, EngineState[F], SeqEvent](
        clearQ(qid).withEvent(UpdateQueueClear(qid)).toHandle
      )
    )

//    private def setObserverAndSelect(
//      sid:      Observation.Id,
//      observer: Observer,
//      user:     User,
//      clientId: ClientId
//    ): HandlerType[F, Unit] = Handle(
//      StateT[F, EngineState[F], (Unit, Option[Stream[F, EventType[F]]])] { st: EngineState[F] =>
//        EngineState
//          .atSequence(sid)
//          .getOption(st)
//          .map { obsseq =>
//            (EngineState
//              .sequences[F]
//              .modify(_ + (sid -> obsseq.copy(observer = observer.some))) >>>
//              refreshSequence(sid) >>>
//              EngineState.instrumentLoadedL[F](obsseq.seqGen.instrument).replace(sid.some) >>> {
//                (_,
//                 ((),
//                  Stream[Pure, EventType[F]](
//                    Event.modifyState[F, EngineState[F], SeqEvent](
//                      { s: EngineState[F] => (
//                         s,
//                         AddLoadedSequence(obsseq.seqGen.instrument,
//                                           sid,
//                                           user,
//                                           clientId
//                         ): SeqEvent
//                        )
//                      }.toHandle
//                    )
//                  ).covary[F].some
//                 )
//                )
//              })(st)
//          }
//          .getOrElse((st, ((), None)))
//          .pure[F]
//      }
//    )

//    private def runSequences(
//      ss:       Set[Observation.Id],
//      observer: Observer,
//      user:     User,
//      clientId: ClientId
//    ): HandlerType[F, List[(Observation.Id, ObserveStep.Id)]] =
//      ss.map(sid =>
//        setObserverAndSelect(sid, observer, user, clientId) *>
//          executeEngine.start(sid).reversedStreamFlatMap(_ => sequenceStart(sid))
//      ).toList
//        .sequence
//        .map(_.collect { case Some((sid, stepId)) => (sid, stepId) })

//    /**
//     * runQueue starts the queue. It founds the top eligible sequences in the queue, and runs them.
//     */
//    private def runQueue(
//      qid:      QueueId,
//      observer: Observer,
//      user:     User,
//      clientId: ClientId
//    ): HandlerType[F, List[(Observation.Id, ObserveStep.Id)]] =
//      executeEngine.get
//        .map(findRunnableObservations(qid))
//        .flatMap(runSequences(_, observer, user, clientId))
//
//    /**
//     * runNextsInQueue continues running the queue after a sequence completes. It finds the next
//     * eligible sequences in the queue, and runs them. At any given time a queue can be running, but
//     * one of the top eligible sequences are not. That is the case if the sequence ended with an
//     * error or is stopped by the user. In both cases, the sequence should not be restarted without
//     * user intervention, nor other sequence that uses the same resources should be started. Because
//     * of that, runNextsInQueue only runs sequences that are now eligible because of the resources
//     * that the just completed sequence has freed.
//     */
//    private def runNextsInQueue(
//      qid:      QueueId,
//      observer: Observer,
//      user:     User,
//      clientId: ClientId,
//      freed:    Set[Resource]
//    ): HandlerType[F, List[(Observation.Id, ObserveStep.Id)]] =
//      executeEngine.get
//        .map(nextRunnableObservations(qid, freed))
//        .flatMap(runSequences(_, observer, user, clientId))
//
    override def startQueue(
      qid:      QueueId,
      observer: Observer,
      user:     User,
      clientId: ClientId
    ): F[Unit] = Applicative[F].unit
    //    executeEngine.offer(
//      Event.modifyState[F, EngineState[F], SeqEvent](executeEngine.get.flatMap { st =>
//        queueO(qid)
//          .getOption(st)
//          .filterNot(_.queue.isEmpty)
//          .map {
//            _.status(st) match {
//              case BatchExecState.Idle | BatchExecState.Stopping =>
//                (EngineState
//                  .queues[F]
//                  .andThen(mapIndex[QueueId, ExecutionQueue].index(qid))
//                  .andThen(ExecutionQueue.cmdState)
//                  .replace(BatchCommandState.Run(observer, user, clientId)) >>> {
//                  (_, ())
//                }).toHandle *>
//                  runQueue(qid, observer, user, clientId)
//              case _                                             => executeEngine.pure(List.empty)
//            }
//          }
//          .getOrElse(executeEngine.pure(List.empty))
//          .map(StartQueue(qid, clientId, _))
//      })
//    )
//
//    private def stopSequencesInQueue(qid: QueueId): HandlerType[F, Unit] =
//      executeEngine.get
//        .map(st =>
//          queueO(qid)
//            .getOption(st)
//            .foldMap(
//              _.queue.filter(sid =>
//                EngineState
//                  .sequenceStateIndex[F](sid)
//                  .getOption(st)
//                  .exists(_.status.isRunning)
//              )
//            )
//        )
//        .flatMap(_.map(executeEngine.pause).fold(executeEngine.unit)(_ *> _))
//
    override def stopQueue(qid: QueueId, clientId: ClientId): F[Unit] =
      Applicative[F].unit
//      executeEngine.offer(
//        Event.modifyState[F, EngineState[F], SeqEvent](
//          executeEngine.get
//            .flatMap { st =>
//              queueO(qid)
//                .getOption(st)
//                .map {
//                  _.status(st) match {
//                    case BatchExecState.Running =>
//                      (cmdStateO(qid).replace(BatchCommandState.Stop) >>> { (_, ()) }).toHandle *>
//                        stopSequencesInQueue(qid)
//                    case BatchExecState.Waiting =>
//                      (cmdStateO(qid).replace(BatchCommandState.Stop) >>> { (_, ()) }).toHandle
//                    case _                      => executeEngine.unit
//                  }
//                }
//                .getOrElse(executeEngine.unit)
//            }
//            .as(StopQueue(qid, clientId))
//        )
//      )
//
    private def engineEventsHook
      : PartialFunction[SystemEvent,
                        Handle[F, EngineState[F], Event[F, EngineState[F], SeqEvent], Unit]
      ] = { case SystemEvent.Finished(sid) =>
      executeEngine.liftF[Unit](systems.odb.sequenceEnd(sid).void)
    }
//    {
//      // Responds to events that could trigger the scheduling of the next sequence in the queue:
//      case SystemEvent.Finished(sid) =>
//        executeEngine.get.flatMap(st =>
//          st.sequences
//            .get(sid)
//            .flatMap { seq =>
//              val freed = seq.seqGen.resources
//              st.queues.collectFirst {
//                case (qid, q @ ExecutionQueue(_, BatchCommandState.Run(observer, user, clid), _))
//                    if q.status(st) =!= BatchExecState.Completed =>
//                  runNextsInQueue(qid, observer, user, clid, freed).flatMap { l =>
//                    Handle.fromStream(
//                      Stream.emit[F, EventType[F]](
//                        Event.modifyState[F, EngineState[F], SeqEvent](
//                          executeEngine.pure(SequencesStart(l))
//                        )
//                      )
//                    )
//                  }
//              }
//            }
//            .getOrElse(executeEngine.unit)
//        )
//    }

    private def configSystemCheck(
      sys: Resource | Instrument,
      st:  EngineState[F]
    ): Boolean = {
      // Resources used by running sequences
      val used = resourcesInUse(st)

      // Resources reserved by running queues, excluding `sid` to prevent self blocking
//      val reservedByQueues = resourcesReserved(EngineState.sequences[F].modify(_ - sid)(st))
//
//      !(used ++ reservedByQueues).contains(sys)
      !used.contains(sys)
    }

    private def configSystemHandle(
      sid:      Observation.Id,
      stepId:   Step.Id,
      sys:      Resource | Instrument,
      clientID: ClientId
    ): HandlerType[F, SeqEvent] =
      executeEngine.get.flatMap { st =>
        if (configSystemCheck(sys, st)) {
          st.sequences
            .get(sid)
            .flatMap(_.seqGen.configActionCoord(stepId, sys))
            .map(c =>
              executeEngine.startSingle(ActionCoords(sid, c)).map[SeqEvent] {
                case EventResult.Outcome.Ok => StartSysConfig(sid, stepId, sys)
                case _                      => NullSeqEvent
              }
            )
            .getOrElse(executeEngine.pure(NullSeqEvent))
        } else {
          executeEngine.pure(ResourceBusy(sid, stepId, sys, clientID))
        }
      }

    /**
     * Triggers the application of a specific step configuration to a system /
     */
    override def configSystem(
      sid:      Observation.Id,
      observer: Observer,
      user:     User,
      stepId:   Step.Id,
      sys:      Resource | Instrument,
      clientID: ClientId
    ): F[Unit] = setObserver(sid, user, observer) *>
      executeEngine.offer(
        Event.modifyState[F, EngineState[F], SeqEvent](
          configSystemHandle(sid, stepId, sys, clientID)
        )
      )

    def notifyODB(
      i: (EventResult[SeqEvent], EngineState[F])
    ): F[(EventResult[SeqEvent], EngineState[F])] =
      (i match {
        case (SystemUpdate(SystemEvent.Failed(id, _, e), _), _) =>
          Logger[F].error(s"Error executing $id due to $e") <*
            systems.odb
              .stepAbort(id)
              .ensure(
                ObserveFailure
                  .Unexpected("Unable to send ObservationAborted message to ODB.")
              )(identity)
        case _                                                  => Applicative[F].unit
      }).as(i)

    private def updateSequenceEndo(
      conditions: Conditions,
      operator:   Option[Operator]
    ): Endo[SequenceData[F]] = (sd: SequenceData[F]) =>
      SequenceData.seq.modify(
        executeEngine.updateSteps(
          toStepList(
            sd.seqGen,
            sd.overrides,
            HeaderExtraData(conditions, operator, sd.observer)
          )
        )
      )(sd)

    private def refreshSequence(id: Observation.Id): Endo[EngineState[F]] = (st: EngineState[F]) =>
      EngineState.atSequence(id).modify(updateSequenceEndo(st.conditions, st.operator))(st)

    private def refreshSequences: Endo[EngineState[F]] = (st: EngineState[F]) =>
      List(
        EngineState.gmosNorthSequence[F],
        EngineState.gmosSouthSequence[F]
      ).map(_.modify(updateSequenceEndo(st.conditions, st.operator)))
        .combineAll(MonoidK[Endo].algebra)(st)

    private def toggleOverride(
      resource: String,
      modify:   (SubsystemEnabled, SystemOverrides) => SystemOverrides,
      event:    SeqEvent,
      seqId:    Observation.Id,
      user:     User,
      enabled:  SubsystemEnabled,
      clientId: ClientId
    ): F[Unit] =
      logDebugEvent(
        s"ObserveEngine: Setting $resource enabled flag to '$enabled' for sequence '$seqId'",
        user,
        clientId
      ) *>
        executeEngine.offer(
          Event.modifyState[F, EngineState[F], SeqEvent](
            (EngineState
              .atSequence(seqId)
              .modify(SequenceData.overrides.modify { x =>
                modify(enabled, x)
              }) >>>
              refreshSequence(seqId)).withEvent(event).toHandle
          )
        )
    override def setTcsEnabled(
      seqId:    Observation.Id,
      user:     User,
      enabled:  SubsystemEnabled,
      clientId: ClientId
    ): F[Unit] =
      toggleOverride(Resource.TCS.label,
                     (enabled, x) => if (enabled.value) x.enableTcs else x.disableTcs,
                     SetTcsEnabled(seqId, user.some, enabled),
                     seqId,
                     user,
                     enabled,
                     clientId
      )

    override def setGcalEnabled(
      seqId:    Observation.Id,
      user:     User,
      enabled:  SubsystemEnabled,
      clientId: ClientId
    ): F[Unit] =
      toggleOverride(Resource.Gcal.label,
                     (enabled, x) => if (enabled.value) x.enableGcal else x.disableGcal,
                     SetGcalEnabled(seqId, user.some, enabled),
                     seqId,
                     user,
                     enabled,
                     clientId
      )

    override def setInstrumentEnabled(
      seqId:    Observation.Id,
      user:     User,
      enabled:  SubsystemEnabled,
      clientId: ClientId
    ): F[Unit] =
      toggleOverride(
        "Instrument",
        (enabled, x) => if (enabled.value) x.enableInstrument else x.disableInstrument,
        SetInstrumentEnabled(seqId, user.some, enabled),
        seqId,
        user,
        enabled,
        clientId
      )

    override def setDhsEnabled(
      seqId:    Observation.Id,
      user:     User,
      enabled:  SubsystemEnabled,
      clientId: ClientId
    ): F[Unit] =
      toggleOverride("DHS",
                     (enabled, x) => if (enabled.value) x.enableDhs else x.disableDhs,
                     SetDhsEnabled(seqId, user.some, enabled),
                     seqId,
                     user,
                     enabled,
                     clientId: ClientId
      )

  }

  def createTranslator[F[_]: Async: Logger](
    site:          Site,
    systems:       Systems[F],
    conditionsRef: Ref[F, Conditions]
  ): F[SeqTranslate[F]] =
    SeqTranslate(site, systems, conditionsRef)

  private def splitWhere[A](l: List[A])(p: A => Boolean): (List[A], List[A]) =
    l.splitAt(l.indexWhere(p))

  private def observations[F[_]](st: EngineState[F]): List[SequenceData[F]] =
    List(st.selected.gmosSouth, st.selected.gmosNorth).flattenOption

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
  private def resourcesInUse[F[_]](st: EngineState[F]): Set[Resource | Instrument] =
    observations(st)
      .mapFilter(s => s.seq.status.isRunning.option(s.seqGen.resources))
      .foldK ++
      systemsBeingConfigured(st)

  /**
   * Resources reserved by running queues.
   */
  private def resourcesReserved[F[_]](st: EngineState[F]): Set[Resource | Instrument] = {
    def reserved(q: ExecutionQueue): Set[Resource | Instrument] = q.queue.collect {
      case s if !s.state.isCompleted => s.resources
    }.foldK

    val runningQs = st.queues.values.filter(_.status(st).running)

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

  /**
   * Build Observe and setup epics
   */
  def build[F[_]: Async: Logger](
    site:    Site,
    systems: Systems[F],
    conf:    ObserveEngineConfiguration
  ): F[ObserveEngine[F]] = for {
    rc  <- Ref.of[F, Conditions](Conditions.Default)
    tr  <- createTranslator(site, systems, rc)
    eng <- Engine.build[F, EngineState[F], SeqEvent](EngineState.engineState[F],
                                                     onAtomComplete[F](systems.odb, tr),
                                                     onAtomReload[F](systems.odb, tr)
           )
  } yield new ObserveEngineImpl[F](eng, systems, conf, tr, rc)

  private def buildObserveStateStream[F[_]: Functor](
    svs:      => SequencesQueue[SequenceView],
    odbProxy: OdbProxy[F]
  ): Stream[F, TargetedClientEvent] =
    Stream.eval:
      odbProxy.getCurrentRecordedIds.map: recordedIds =>
        ObserveState.fromSequenceViewQueue(svs, recordedIds)

  private def modifyStateEvent[F[_]: Functor](
    v:        SeqEvent,
    svs:      => SequencesQueue[SequenceView],
    odbProxy: OdbProxy[F]
  ): Stream[F, TargetedClientEvent] =
    v match
      case RequestConfirmation(c @ UserPrompt.ChecksOverride(_, _, _), cid) =>
        Stream.emit(ClientEvent.ChecksOverrideEvent(c).forClient(cid))
      // case RequestConfirmation(m, cid)        => Stream.emit(UserPromptNotification(m, cid))
      case StartSysConfig(oid, stepId, res)                                 =>
        Stream.emit[F, TargetedClientEvent](
          SingleActionEvent(oid, stepId, res, ClientEvent.SingleActionState.Started, none)
        ) ++ buildObserveStateStream(svs, odbProxy)
      // case Busy(id, cid)                      => Stream.emit(UserNotification(ResourceConflict(id), cid))
      // case ResourceBusy(oid, sid, res, cid)   =>
      //   Stream.emit(UserNotification(SubsystemBusy(oid, sid, res), cid))
      // case NoMoreAtoms(_)                     => Stream.empty
      case NewAtomLoaded(obsId, sequenceType, atomId)                       =>
        Stream.emit[F, TargetedClientEvent](ClientEvent.AtomLoaded(obsId, sequenceType, atomId)) ++
          buildObserveStateStream(svs, odbProxy)
      case e if e.isModelUpdate                                             =>
        buildObserveStateStream(svs, odbProxy)
      case _                                                                => Stream.empty

  private def executionQueueViews[F[_]](
    st: EngineState[F]
  ): SortedMap[QueueId, ExecutionQueueView] =
    SortedMap(st.queues.map { case (qid, q) =>
      qid -> ExecutionQueueView(qid, q.name, q.cmdState, q.status(st), q.queue.map(_.obsId))
    }.toList*)

  private def viewSequence[F[_]](obsSeq: SequenceData[F]): SequenceView = {
    val st         = obsSeq.seq
    val seq        = st.toSequence
    val instrument = obsSeq.seqGen.instrument
    val seqType    = obsSeq.seqGen.nextAtom.sequenceType

    def resources(s: SequenceGen.StepGen[F]): List[Resource | Instrument] = s match {
      case s: SequenceGen.PendingStepGen[F] => s.resources.toList
      case _                                => List.empty
    }

    def engineSteps(seq: Sequence[F]): List[ObserveStep] =
      obsSeq.seqGen.nextAtom.steps.zip(seq.steps).map { case (a, b) =>
        val stepResources =
          resources(a).mapFilter(x =>
            obsSeq.seqGen
              .configActionCoord(a.id, x)
              .map(i => (x, obsSeq.seq.getSingleState(i).actionStatus))
          )
        StepsView
          .stepsView(instrument)
          .stepView(
            a,
            b,
            stepResources,
            obsSeq.pendingObsCmd
          )
      } match {
        // The sequence could be empty
        case Nil => Nil
        // Find first Pending ObserveStep when no ObserveStep is Running and mark it as Running
        case steps
            if Sequence.State.isRunning(st) && steps.forall(_.status =!= StepState.Running) =>
          val (xs, ys) = splitWhere(steps)(_.status === StepState.Pending)
          xs ++ ys.headOption.map(ObserveStep.status.replace(StepState.Running)).toList ++ ys.tail
        case steps
            if st.status === SequenceState.Idle && steps.exists(_.status === StepState.Running) =>
          val (xs, ys) = splitWhere(steps)(_.status === StepState.Running)
          xs ++ ys.headOption.map(ObserveStep.status.replace(StepState.Paused)).toList ++ ys.tail
        case x   => x
      }

    val engSteps      = engineSteps(seq)
    val stepResources = engSteps.map {
      case ObserveStep.Standard(id, _, _, _, _, _, _, configStatus, _)         =>
        id -> configStatus.toMap
      case ObserveStep.NodAndShuffle(id, _, _, _, _, _, _, configStatus, _, _) =>
        id -> configStatus.toMap
    }.toMap

    // TODO: Implement willStopIn
    SequenceView(
      seq.id,
      SequenceMetadata(instrument, obsSeq.observer, obsSeq.seqGen.obsData.title),
      st.status,
      obsSeq.overrides,
      seqType,
      engSteps,
      None,
      stepResources
    )
  }

  private def toClientEvent[F[_]: Functor](
    ev:       EventResult[SeqEvent],
    qState:   EngineState[F],
    odbProxy: OdbProxy[F]
  ): Stream[F, TargetedClientEvent] = {
    val sequences: List[SequenceView]     =
      qState.sequences.view.values.map(viewSequence).toList
    // Building the view is a relatively expensive operation
    // By putting it into a def we only incur that cost if the message requires it
    def svs: SequencesQueue[SequenceView] =
      SequencesQueue(
        List(
          qState.selected.gmosSouth.map(x => Instrument.GmosSouth -> x.seqGen.obsData.id),
          qState.selected.gmosNorth.map(x => Instrument.GmosNorth -> x.seqGen.obsData.id)
        ).flattenOption.toMap,
        qState.conditions,
        qState.operator,
        executionQueueViews(qState),
        sequences
      )

    ev match
      case UserCommandResponse(ue, _, uev) =>
        ue match
          case UserEvent.Pure(NotifyUser(m, cid)) =>
            Stream.emit(UserNotification(m).forClient(cid))
          case UserEvent.ModifyState(_)           =>
            modifyStateEvent(uev.getOrElse(NullSeqEvent), svs, odbProxy)
          case e if e.isModelUpdate               => buildObserveStateStream(svs, odbProxy)
          // case UserEvent.LogInfo(m, ts)          => Stream.emit(ServerLogMessage(ServerLogLevel.INFO, ts, m))
          // case UserEvent.LogWarning(m, ts)       =>
          //   Stream.emit(ServerLogMessage(ServerLogLevel.WARN, ts, m))
          // case UserEvent.LogError(m, ts)         =>
          //   Stream.emit(ServerLogMessage(ServerLogLevel.ERROR, ts, m))
          case _                                  => Stream.empty
      case SystemUpdate(se, _)             =>
        se match
          // TODO: Sequence completed event not emitted by engine.
          case SystemEvent.PartialResult(i, s, _, Partial(ObsProgress(t, r, v)))   =>
            Stream.emit(
              ProgressEvent(ObservationProgress(i, StepProgress.Regular(s, t, r.self, v)))
            )
          case SystemEvent.PartialResult(i, s, _, Partial(NsProgress(t, r, v, u))) =>
            Stream.emit(
              ProgressEvent(ObservationProgress(i, StepProgress.NodAndShuffle(s, t, r.self, v, u)))
            )
          // case SystemEvent.Busy(id, clientId)                                       =>
          //   Stream.emit(UserNotification(ResourceConflict(id), clientId))
          case SystemEvent.SingleRunCompleted(c, _)                                =>
            Stream.emits(
              singleActionClientEvent(
                c,
                qState,
                ClientEvent.SingleActionState.Completed
              ).toList
            ) ++ buildObserveStateStream(svs, odbProxy)
          case SystemEvent.SingleRunFailed(c, r)                                   =>
            Stream.emits(
              singleActionClientEvent(
                c,
                qState,
                ClientEvent.SingleActionState.Failed,
                r.msg.some
              ).toList
            ) ++ buildObserveStateStream(svs, odbProxy)
          case e if e.isModelUpdate                                                =>
            buildObserveStateStream(svs, odbProxy)
          case _                                                                   => Stream.empty
  }

  private def singleActionClientEvent[F[_]](
    c:            ActionCoords,
    qState:       EngineState[F],
    clientAction: ClientEvent.SingleActionState,
    errorMsg:     Option[String] = none
  ): Option[TargetedClientEvent] =
    qState.sequences
      .get(c.sid)
      .flatMap(_.seqGen.resourceAtCoords(c.actCoords))
      .map(res => SingleActionEvent(c.sid, c.actCoords.stepId, res, clientAction, errorMsg))

  private def onAtomComplete[F[_]: Monad](
    odb:           OdbProxy[F],
    translator:    SeqTranslate[F]
  )(
    executeEngine: Engine[F, EngineState[F], SeqEvent],
    obsId:         Observation.Id
  ): Handle[F, EngineState[F], Event[F, EngineState[F], SeqEvent], SeqEvent] =
    Handle
      .get[F, EngineState[F], Event[F, EngineState[F], SeqEvent]]
      .map(EngineState.atSequence[F](obsId).getOption)
      .flatMap {
        _.map { seq =>
          Handle.liftF[F, EngineState[F], Event[F, EngineState[F], SeqEvent], Boolean](
            odb.atomEnd(obsId)
          ) *>
            (seq.seqGen.nextAtom.sequenceType match {
              case SequenceType.Acquisition =>
                Handle.pure[F, EngineState[F], Event[F, EngineState[F], SeqEvent], SeqEvent](
                  SeqEvent.AtomCompleted(obsId,
                                         SequenceType.Acquisition,
                                         seq.seqGen.nextAtom.atomId
                  )
                )
              case SequenceType.Science     =>
                tryNewAtom[F](odb, translator, executeEngine, obsId, SequenceType.Science)
                  .as(
                    SeqEvent.AtomCompleted(obsId, SequenceType.Science, seq.seqGen.nextAtom.atomId)
                  )
            })
        }.getOrElse(
          Handle.pure[F, EngineState[F], Event[F, EngineState[F], SeqEvent], SeqEvent](NullSeqEvent)
        )
      }

  private def updateAtom[F[_]](obsId: Observation.Id, atm: AtomGen[F]) = (st: EngineState[F]) =>
    EngineState
      .atSequence[F](obsId)
      .modify { seq =>
        val sg: SequenceData[F] =
          seq.focus(_.seqGen.nextAtom).replace(atm)
        sg
          .focus(_.seq)
          .modify(s =>
            val ns = Sequence.State.init(
              Sequence.sequence[F](
                obsId,
                atm.atomId,
                toStepList(
                  sg.seqGen,
                  sg.overrides,
                  HeaderExtraData(st.conditions, st.operator, sg.observer)
                )
              )
            )
            Sequence.State.status.replace(s.status)(ns)
          )
      }(st)

  private def tryNewAtom[F[_]: Monad](
    odb:           OdbProxy[F],
    translator:    SeqTranslate[F],
    executeEngine: Engine[F, EngineState[F], SeqEvent],
    obsId:         Observation.Id,
    atomType:      SequenceType
  ): Handle[F, EngineState[F], Event[F, EngineState[F], SeqEvent], Unit] =
    Handle
      .fromStream[F, EngineState[F], Event[F, EngineState[F], SeqEvent]](
        Stream.eval {
          odb.read(obsId).map { x =>
            translator
              .nextAtom(x, atomType)
              ._2
              .map { atm =>
                Event.modifyState[F, EngineState[F], SeqEvent]({ (st: EngineState[F]) =>
                    val inst: Instrument = EngineState
                      .atSequence[F](obsId)
                      .getOption(st)
                      .map(_.seqGen.instrument)
                      .getOrElse(Instrument.GmosNorth)
                    val state            = updateAtom(obsId, atm)(st)
                    (state, inst)
                  }.toHandle.flatMap(inst =>
                    executeEngine.startNewAtom(obsId) *>
                      Handle.liftF[F, EngineState[F], Event[F, EngineState[F], SeqEvent], SeqEvent](
                        odb
                          .atomStart(
                            obsId,
                            inst,
                            atm.sequenceType,
                            NonNegShort.unsafeFrom(atm.steps.length.toShort),
                            atm.atomId.some
                          )
                          .as(SeqEvent.NewAtomLoaded(obsId, atm.sequenceType, atm.atomId))
                      )
                  )
                )
              }
              .getOrElse(
                Event.modifyState[F, EngineState[F], SeqEvent](
                  executeEngine.startNewAtom(obsId).as(SeqEvent.NoMoreAtoms(obsId))
                )
              )
          }
        }
      )

  private def onAtomReload[F[_]: Monad](
    odb:           OdbProxy[F],
    translator:    SeqTranslate[F]
  )(
    executeEngine: Engine[F, EngineState[F], SeqEvent],
    obsId:         Observation.Id
  ): Handle[F, EngineState[F], Event[F, EngineState[F], SeqEvent], SeqEvent] =
    Handle
      .get[F, EngineState[F], Event[F, EngineState[F], SeqEvent]]
      .map(EngineState.atSequence[F](obsId).getOption)
      .flatMap {
        _.map { seq =>
          seq.seqGen.nextAtom.sequenceType match {
            // We shouldn't reload acq because odb always gives more steps for acq
            case SequenceType.Acquisition =>
              executeEngine.startNewAtom(obsId).as(NullSeqEvent)
            case SequenceType.Science     =>
              tryAtomReload[F](odb, translator, executeEngine, obsId, SequenceType.Science)
                .as(SeqEvent.NullSeqEvent)
          }
        }.getOrElse(
          Handle.pure[F, EngineState[F], Event[F, EngineState[F], SeqEvent], SeqEvent](NullSeqEvent)
        )
      }

  private def tryAtomReload[F[_]: Monad](
    odb:           OdbProxy[F],
    translator:    SeqTranslate[F],
    executeEngine: Engine[F, EngineState[F], SeqEvent],
    obsId:         Observation.Id,
    atomType:      SequenceType
  ): Handle[F, EngineState[F], Event[F, EngineState[F], SeqEvent], Unit] =
    Handle
      .fromStream[F, EngineState[F], Event[F, EngineState[F], SeqEvent]](Stream.eval {
        odb.read(obsId).map { x =>
          atomType match {
            case SequenceType.Acquisition =>
              Event.nullEvent
            case SequenceType.Science     =>
              // Read the next atom from the odb and replaces the current atom
              translator
                .nextAtom(x, atomType)
                ._2
                .map { atm =>
                  Event.modifyState[F, EngineState[F], SeqEvent]({ (st: EngineState[F]) =>
                      val state = updateAtom(obsId, atm)(st)
                      (state, ())
                    }.toHandle
                      .flatMap(_ => executeEngine.startNewAtom(obsId).as(SeqEvent.NullSeqEvent))
                  )
                }
                .getOrElse(Event.nullEvent)
          }
        }
      })
}
