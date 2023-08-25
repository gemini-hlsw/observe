// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.{Applicative, Endo}
import cats.data.NonEmptyList
import cats.effect.kernel.Sync
import cats.effect.{Async, Ref, Temporal}
import cats.syntax.all.*
import fs2.{Pipe, Stream}
import lucuma.core.enums.{CloudExtinction, ImageQuality, Site, SkyBackground, WaterVapor}
import lucuma.core.model.{ConstraintSet, Observation}
import lucuma.core.model.sequence.StepConfig as OcsStepConfig
import monocle.{Focus, Optional}
import monocle.function.Index.mapIndex
import mouse.all.*
import observe.engine.EventResult.*
import observe.engine.Result.Partial
import observe.engine.{Handle, Step as _, SystemEvent, UserEvent, *}
import observe.model.NodAndShuffleStep.{PauseGracefully, PendingObserveCmd, StopGracefully}
import observe.model.Notification.*
import observe.model.given
import observe.model.UserPrompt.{
  Discrepancy,
  ObsConditionsCheckOverride,
  SeqCheck,
  TargetCheckOverride
}
import observe.model.{StepId, UserDetails, *}
import observe.model.enums.{BatchExecState, Instrument, Resource, RunOverride, ServerLogLevel}
import observe.model.config.*
//import observe.model.enums.{ImageQuality as _, *}
import observe.model.events.{SequenceStart as ClientSequenceStart, *}
import observe.common.ObsQueriesGQL
import org.typelevel.log4cats.Logger

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.collection.immutable.SortedMap
import scala.concurrent.duration.*
import SeqEvent.*

trait ObserveEngine[F[_]] {

  val systems: Systems[F]

  def sync(q: EventQueue[F], seqId: Observation.Id): F[Unit]

  def start(
    q:           EventQueue[F],
    id:          Observation.Id,
    user:        UserDetails,
    observer:    Observer,
    clientId:    ClientId,
    runOverride: RunOverride
  ): F[Unit]

  def startFrom(
    q:           EventQueue[F],
    id:          Observation.Id,
    observer:    Observer,
    stp:         StepId,
    clientId:    ClientId,
    runOverride: RunOverride
  ): F[Unit]

  def requestPause(
    q:        EventQueue[F],
    id:       Observation.Id,
    observer: Observer,
    user:     UserDetails
  ): F[Unit]

  def requestCancelPause(
    q:        EventQueue[F],
    id:       Observation.Id,
    observer: Observer,
    user:     UserDetails
  ): F[Unit]

  def setBreakpoint(
    q:        EventQueue[F],
    seqId:    Observation.Id,
    user:     UserDetails,
    observer: Observer,
    stepId:   StepId,
    v:        Boolean
  ): F[Unit]

  def setOperator(q: EventQueue[F], user: UserDetails, name: Operator): F[Unit]

  def setObserver(
    q:     EventQueue[F],
    seqId: Observation.Id,
    user:  UserDetails,
    name:  Observer
  ): F[Unit]

  // Systems overrides
  def setTcsEnabled(
    q:       EventQueue[F],
    seqId:   Observation.Id,
    user:    UserDetails,
    enabled: Boolean
  ): F[Unit]

  def setGcalEnabled(
    q:       EventQueue[F],
    seqId:   Observation.Id,
    user:    UserDetails,
    enabled: Boolean
  ): F[Unit]

  def setInstrumentEnabled(
    q:       EventQueue[F],
    seqId:   Observation.Id,
    user:    UserDetails,
    enabled: Boolean
  ): F[Unit]

  def setDhsEnabled(
    q:       EventQueue[F],
    seqId:   Observation.Id,
    user:    UserDetails,
    enabled: Boolean
  ): F[Unit]

  def selectSequence(
    q:        EventQueue[F],
    i:        Instrument,
    sid:      Observation.Id,
    observer: Observer,
    user:     UserDetails,
    clientId: ClientId
  ): F[Unit]

  def clearLoadedSequences(q: EventQueue[F], user: UserDetails): F[Unit]

  def resetConditions(q: EventQueue[F]): F[Unit]

  def setConditions(q: EventQueue[F], conditions: Conditions, user: UserDetails): F[Unit]

  def setImageQuality(q: EventQueue[F], iq: ImageQuality, user: UserDetails): F[Unit]

  def setWaterVapor(q: EventQueue[F], wv: WaterVapor, user: UserDetails): F[Unit]

  def setSkyBackground(q: EventQueue[F], sb: SkyBackground, user: UserDetails): F[Unit]

  def setCloudExtinction(q: EventQueue[F], cc: CloudExtinction, user: UserDetails): F[Unit]

  def setSkipMark(
    q:        EventQueue[F],
    seqId:    Observation.Id,
    user:     UserDetails,
    observer: Observer,
    stepId:   StepId,
    v:        Boolean
  ): F[Unit]

  def requestRefresh(q: EventQueue[F], clientId: ClientId): F[Unit]

  def stopObserve(
    q:        EventQueue[F],
    seqId:    Observation.Id,
    observer: Observer,
    user:     UserDetails,
    graceful: Boolean
  ): F[Unit]

  def abortObserve(
    q:        EventQueue[F],
    seqId:    Observation.Id,
    observer: Observer,
    user:     UserDetails
  ): F[Unit]

  def pauseObserve(
    q:        EventQueue[F],
    seqId:    Observation.Id,
    observer: Observer,
    user:     UserDetails,
    graceful: Boolean
  ): F[Unit]

  def resumeObserve(
    q:        EventQueue[F],
    seqId:    Observation.Id,
    observer: Observer,
    user:     UserDetails
  ): F[Unit]

  def addSequencesToQueue(q: EventQueue[F], qid: QueueId, seqIds: List[Observation.Id]): F[Unit]

  def addSequenceToQueue(q: EventQueue[F], qid: QueueId, seqId: Observation.Id): F[Unit]

  def removeSequenceFromQueue(q: EventQueue[F], qid: QueueId, seqId: Observation.Id): F[Unit]

  def moveSequenceInQueue(
    q:     EventQueue[F],
    qid:   QueueId,
    seqId: Observation.Id,
    delta: Int,
    cid:   ClientId
  ): F[Unit]

  def clearQueue(q: EventQueue[F], qid: QueueId): F[Unit]

  def startQueue(
    q:        EventQueue[F],
    qid:      QueueId,
    observer: Observer,
    user:     UserDetails,
    clientId: ClientId
  ): F[Unit]

  def stopQueue(q: EventQueue[F], qid: QueueId, clientId: ClientId): F[Unit]

  /**
   * Triggers the application of a specific step configuration to a system
   */
  def configSystem(
    q:        EventQueue[F],
    sid:      Observation.Id,
    observer: Observer,
    user:     UserDetails,
    stepId:   StepId,
    sys:      Resource,
    clientID: ClientId
  ): F[Unit]

  def eventStream(q: EventQueue[F]): Stream[F, ObserveEvent]

  // Used by tests
  def stream(p: Stream[F, EventType[F]])(
    s0: EngineState[F]
  ): Stream[F, (EventResult[SeqEvent], EngineState[F])]
}

object ObserveEngine {

  private class ObserveEngineImpl[F[_]: Async: Logger](
    override val systems:        Systems[F],
    @annotation.unused settings: ObserveEngineConfiguration,
    sm:                          ObserveMetrics,
    translator:                  SeqTranslate[F],
    conditionsRef:               Ref[F, Conditions]
  )(using
    executeEngine:               Engine[F, EngineState[F], SeqEvent] // observe.server.ExecEngineType[F]
  ) extends ObserveEngine[F] {

    //    private val odbLoader = new ODBSequencesLoader[F](systems.odb, translators)
    //
    override def sync(q: EventQueue[F], seqId: Observation.Id): F[Unit] = Applicative[F].unit
    //      odbLoader.loadEvents(seqId).flatMap(_.map(q.offer).sequence.void)
    //
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
      stepId: Option[StepId]
    ): Option[SequenceGen.StepGen[F]] = for {
      stp    <- stepId.orElse(obs.seq.currentStep.map(_.id))
      stpGen <- obs.seqGen.steps.find(_.id === stp)
    } yield stpGen

    private def findFirstCheckRequiredStep(
      obs:    SequenceData[F],
      stepId: StepId
    ): Option[SequenceGen.StepGen[F]] =
      obs.seqGen.steps.dropWhile(_.id =!= stepId).find(a => stepRequiresChecks(a.config))

    /**
     * Check if the target on the TCS matches the Observe target
     * @return
     *   an F that returns an optional TargetMatchResult if the targets don't match
     */
    private def sequenceTcsTargetMatch(
      seqData: SequenceData[F]
    ): F[Option[TargetCheckOverride]] =
      seqData.targetName
        .map { seqTarget =>
          systems.tcsKeywordReader.sourceATarget.objectName.map { tcsTarget =>
            (seqTarget =!= tcsTarget).option(
              TargetCheckOverride(UserPrompt.Discrepancy(tcsTarget, seqTarget))
            )
          }
        }
        .getOrElse(none.pure[F])

    /**
     * Extract the target name from a step configuration. Some processing is necessary to get the
     * same string that appears in TCS.
     */
    private def extractTargetName(obs: ObsQueriesGQL.ObsQuery.Data.Observation): Option[String] =
      obs.targetEnvironment.firstScienceTarget.map(_.targetName.toString)

    private def stepRequiresChecks(stepConfig: OcsStepConfig): Boolean = stepConfig match {
      case OcsStepConfig.Gcal(_, _, _, _) => true
      case OcsStepConfig.Science(_, _)    => true
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
      val reqCC              = requiredObsConditions.cloudExtinction
      val reqIQ              = requiredObsConditions.imageQuality
      val reqSB              = requiredObsConditions.skyBackground
      val reqWV              = requiredObsConditions.waterVapor

      val ccCmp = (!checkCloudCover(actualObsConditions.cc, reqCC))
        .option(Discrepancy(actualObsConditions.cc.fold(UnknownStr)(_.label), reqCC.label))

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
         .andThen(Focus[SequenceData[F]](_.pendingObsCmd))
         .replace(None)(s),
       SeqEvent.NullSeqEvent: SeqEvent
      )
    }.toHandle

    private def setObsCmd(id: Observation.Id, cmd: PendingObserveCmd): HandlerType[F, SeqEvent] = {
      (s: EngineState[F]) =>
        (EngineState
           .atSequence[F](id)
           .andThen(Focus[SequenceData[F]](_.pendingObsCmd))
           .replace(cmd.some)(s),
         SeqEvent.NullSeqEvent: SeqEvent
        )
    }.toHandle

    // Produce a Handle that will send a SequenceStart notification to the ODB, and produces the (sequenceId, stepId)
    // if there is a valid sequence with a valid current step.
    private def sequenceStart(
      id: Observation.Id
    ): HandlerType[F, Option[(Observation.Id, StepId)]] =
      executeEngine.get.flatMap { s =>
        EngineState
          .atSequence(id)
          .getOption(s)
          .flatMap { seq =>
            seq.seq.currentStep.flatMap { step =>
              seq.visitId match {
                case Some(_) => none
                case None    =>
                  Handle
                    .fromStream[F, EngineState[F], EventType[F]](
                      Stream.eval[F, EventType[F]](
                        systems.odb
                          .sequenceStart(id, seq.seqGen.staticCfg)
                          .map { i =>
                            Event.modifyState {
                              executeEngine
                                .modify(
                                  EngineState
                                    .atSequence(id)
                                    .andThen(Focus[SequenceData[F]](_.visitId))
                                    .replace(i.some)
                                )
                                .as[SeqEvent](SeqEvent.NullSeqEvent)
                            }
                          }
                      )
                    )
                    .as((id, step.id).some)
                    .some
              }
            }
          }
          .getOrElse(executeEngine.pure(none[(Observation.Id, StepId)]))
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
      stepId:      Option[StepId],
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
                  ststp  <- findStartingStep(seq, stepId)
                  stpidx <- seq.seqGen.stepIndex(ststp.id)
                  sp     <- findFirstCheckRequiredStep(seq, ststp.id)
                } yield sequenceTcsTargetMatch(seq).map { tchk =>
                  (ststp.some,
                   stpidx,
                   List(tchk, observingConditionsMatch(st.conditions, seq.conditions))
                     .collect { case Some(x) => x }
                     .widen[SeqCheck]
                  )
                })
                  .getOrElse((none[SequenceGen.StepGen[F]], 0, List.empty[SeqCheck]).pure[F])
              }
              .flatMap { case (stpg, stpidx, checks) =>
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
                        UserPrompt.ChecksOverride(id, stp.id, stpidx, NonEmptyList(x, xs)),
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
      q:           EventQueue[F],
      id:          Observation.Id,
      user:        UserDetails,
      observer:    Observer,
      clientId:    ClientId,
      runOverride: RunOverride
    ): F[Unit] = q.offer(
      Event.modifyState[F, EngineState[F], SeqEvent](
        setObserver(id, observer) *>
          clearObsCmd(id) *>
          startChecks(executeEngine.start(id), id, clientId, none, runOverride)
      )
    )

    // Stars a sequence from an arbitrary step. All previous non executed steps are skipped.
    // The method checks for resources conflict.
    override def startFrom(
      q:           EventQueue[F],
      id:          Observation.Id,
      observer:    Observer,
      stp:         StepId,
      clientId:    ClientId,
      runOverride: RunOverride
    ): F[Unit] = Applicative[F].unit
//      q.offer(
//        Event.modifyState[F, EngineState[F], SeqEvent](
//          setObserver(id, observer) *>
//            clearObsCmd(id) *>
//            startChecks(executeEngine.startFrom(id, stp), id, clientId, stp.some, runOverride)
//        )
//      )
//
    override def requestPause(
      q:        EventQueue[F],
      seqId:    Observation.Id,
      observer: Observer,
      user:     UserDetails
    ): F[Unit] = Applicative[F].unit
//      setObserver(q, seqId, user, observer) *>
//        q.offer(Event.pause[F, EngineState[F], SeqEvent](seqId, user))
//
    override def requestCancelPause(
      q:        EventQueue[F],
      seqId:    Observation.Id,
      observer: Observer,
      user:     UserDetails
    ): F[Unit] = Applicative[F].unit
//      setObserver(q, seqId, user, observer) *>
//        q.offer(Event.cancelPause[F, EngineState[F], SeqEvent](seqId, user))
//
    override def setBreakpoint(
      q:        EventQueue[F],
      seqId:    Observation.Id,
      user:     UserDetails,
      observer: Observer,
      stepId:   StepId,
      v:        Boolean
    ): F[Unit] = Applicative[F].unit
//      setObserver(q, seqId, user, observer) *>
//        q.offer(Event.breakpoint[F, EngineState[F], SeqEvent](seqId, user, stepId, v))
//
    override def setOperator(q: EventQueue[F], user: UserDetails, name: Operator): F[Unit] =
      Applicative[F].unit
//      logDebugEvent(q, s"ObserveEngine: Setting Operator name to '$name' by ${user.username}") *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            (EngineState.operator[F].replace(name.some) >>> refreshSequences)
//              .withEvent(SetOperator(name, user.some))
//              .toHandle
//          )
//        )
//
    private def setObserver(
      id:       Observation.Id,
      observer: Observer,
      event:    SeqEvent = SeqEvent.NullSeqEvent
    ): HandlerType[F, SeqEvent] = { (s: EngineState[F]) =>
      (EngineState
         .atSequence[F](id)
         .modify(Focus[SequenceData[F]](_.observer).replace(observer.some))(s),
       event
      )
    }.toHandle

    override def setObserver(
      q:     EventQueue[F],
      seqId: Observation.Id,
      user:  UserDetails,
      name:  Observer
    ): F[Unit] = Applicative[F].unit
//      logDebugEvent(
//        q,
//        s"ObserveEngine: Setting Observer name to '$name' for sequence '$seqId' by ${user.username}"
//      ) *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            (EngineState
//              .sequences[F]
//              .andThen(mapIndex[Observation.Id, SequenceData[F]].index(seqId))
//              .modify(Focus[SequenceData](_.observer).replace(name.some)) >>>
//              refreshSequence(seqId)).withEvent(SetObserver(seqId, user.some, name)).toHandle
//          )
//        )
//
//    private def selectSequenceEvent(
//      i:        Instrument,
//      sid:      Observation.Id,
//      observer: Observer,
//      user:     UserDetails,
//      clientId: ClientId
//    ): EventType[F] = {
//      val lens                                     =
//        EngineState
//          .sequences[F]
//          .andThen(mapIndex[Observation.Id, SequenceData[F]].index(sid))
//          .modify(Focus[SequenceData](_.observer).replace(observer.some)) >>>
//          EngineState.instrumentLoadedL[F](i).replace(sid.some) >>>
//          refreshSequence(sid)
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
//
    override def selectSequence(
      q:        EventQueue[F],
      i:        Instrument,
      sid:      Observation.Id,
      observer: Observer,
      user:     UserDetails,
      clientId: ClientId
    ): F[Unit] = Applicative[F].unit
//      Sync[F]
//        .delay(Instant.now)
//        .flatMap { ts =>
//          q.offer(
//            Event.logInfoMsg[F, EngineState[F], SeqEvent](
//              s"User '${user.displayName}' sync and load sequence $sid on ${i.show}",
//              ts
//            )
//          )
//        } *>
//        sync(q, sid) *>
//        q.offer(selectSequenceEvent(i, sid, observer, user, clientId))
//
//    private def logDebugEvent(q: EventQueue[F], msg: String): F[Unit] =
//      Event.logDebugMsgF[F, EngineState[F], SeqEvent](msg).flatMap(q.offer)
//
    override def clearLoadedSequences(q: EventQueue[F], user: UserDetails): F[Unit]        =
      Applicative[F].unit
//      logDebugEvent(q, "ObserveEngine: Updating loaded sequences") *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            EngineState
//              .selected[F]
//              .replace(Map.empty)
//              .withEvent(ClearLoadedSequences(user.some))
//              .toHandle
//          )
//        )
//
    override def resetConditions(q: EventQueue[F]): F[Unit] = Applicative[F].unit
//      logDebugEvent(q, "ObserveEngine: Reset conditions") *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            (EngineState.conditions[F].replace(Conditions.Default) >>> refreshSequences)
//              .withEvent(SetConditions(Conditions.Default, None))
//              .toHandle
//          )
//        )
//
    override def setConditions(
      q:          EventQueue[F],
      conditions: Conditions,
      user:       UserDetails
    ): F[Unit] = Applicative[F].unit
//      logDebugEvent(q, "ObserveEngine: Setting conditions") *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            (EngineState.conditions[F].replace(conditions) >>> refreshSequences)
//              .withEvent(SetConditions(conditions, user.some))
//              .toHandle
//          )
//        )
//
    override def setImageQuality(q: EventQueue[F], iq: ImageQuality, user: UserDetails): F[Unit] =
      Applicative[F].unit
//      logDebugEvent(q, s"ObserveEngine: Setting image quality to $iq") *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            (Focus[EngineState](_.conditions[F])[F].andThen(Conditions.iq).replace(iq) >>> refreshSequences)
//              .withEvent(SetImageQuality(iq, user.some))
//              .toHandle
//          )
//        )
//
    override def setWaterVapor(q: EventQueue[F], wv: WaterVapor, user: UserDetails): F[Unit]       =
      Applicative[F].unit
//      logDebugEvent(q, s"ObserveEngine: Setting water vapor to $wv") *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            (Focus[EngineState](_.conditions[F])[F].andThen(Conditions.wv).replace(wv) >>> refreshSequences)
//              .withEvent(SetWaterVapor(wv, user.some))
//              .toHandle
//          )
//        )
//
    override def setSkyBackground(q: EventQueue[F], sb: SkyBackground, user: UserDetails): F[Unit] =
      Applicative[F].unit
//      logDebugEvent(q, s"ObserveEngine: Setting sky background to $sb") *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            (Focus[EngineState](_.conditions[F])[F].andThen(Conditions.sb).replace(sb) >>> refreshSequences)
//              .withEvent(SetSkyBackground(sb, user.some))
//              .toHandle
//          )
//        )
//
    override def setCloudExtinction(
      q:    EventQueue[F],
      cc:   CloudExtinction,
      user: UserDetails
    ): F[Unit] = Applicative[F].unit
//      logDebugEvent(q, s"ObserveEngine: Setting cloud cover to $cc") *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            (Focus[EngineState](_.conditions[F])[F].andThen(Conditions.cc).replace(cc) >>> refreshSequences)
//              .withEvent(SetCloudCover(cc, user.some))
//              .toHandle
//          )
//        )
//
    override def setSkipMark(
      q:        EventQueue[F],
      seqId:    Observation.Id,
      user:     UserDetails,
      observer: Observer,
      stepId:   StepId,
      v:        Boolean
    ): F[Unit] = Applicative[F].unit
//      setObserver(q, seqId, user, observer) *>
//        q.offer(Event.skip[F, EngineState[F], SeqEvent](seqId, user, stepId, v))
//
    override def requestRefresh(q: EventQueue[F], clientId: ClientId): F[Unit]                     =
      q.offer(Event.poll(clientId))

    private def seqQueueRefreshStream: Stream[F, Either[ObserveFailure, EventType[F]]] =
      Stream.empty
//    {
//      val fd = Duration(settings.odbQueuePollingInterval.toSeconds, TimeUnit.SECONDS)
//      Stream
//        .fixedDelay[F](fd)
//        .evalMap(_ => systems.odb.queuedSequences)
//        .flatMap { x =>
//          Stream.emit(
//            Event
//              .getState[F, EngineState[F], SeqEvent] { st =>
//                Stream.eval(odbLoader.refreshSequenceList(x, st)).flatMap(Stream.emits).some
//              }
//              .asRight
//          )
//        }
//        .handleErrorWith { e =>
//          Stream.emit(ObserveFailure.ObserveException(e).asLeft)
//        }
//    }

    private val heartbeatPeriod: FiniteDuration = FiniteDuration(10, TimeUnit.SECONDS)

    private def heartbeatStream: Stream[F, EventType[F]] = {
      // If there is no heartbeat in 5 periods throw an error
      val noHeartbeatDetection =
        ObserveEngine.failIfNoEmitsWithin[F, EventType[F]](5 * heartbeatPeriod,
                                                           "Engine heartbeat not detected"
        )
      Stream
        .awakeDelay[F](heartbeatPeriod)
        .as(Event.nullEvent: EventType[F])
        .through(noHeartbeatDetection.andThen(_.recoverWith { case _ =>
          Stream.eval[F, EventType[F]](Event.logErrorMsgF("Observe engine heartbeat undetected"))
        }))
    }

    override def eventStream(q: EventQueue[F]): Stream[F, ObserveEvent]        =
      stream(
        Stream
          .fromQueueUnterminated(q)
          .mergeHaltBoth(seqQueueRefreshStream.rethrow.mergeHaltL(heartbeatStream))
      )(EngineState.default[F]).flatMap(x => Stream.eval(notifyODB(x).attempt)).flatMap {
        case Right((ev, qState)) =>
          val sequences = List(qState.selected.gmosNorth, qState.selected.gmosSouth).flattenOption
            .map(viewSequence)
          toObserveEvent[F](ev, qState) <* Stream.eval(updateMetrics(ev, sequences))
        case Left(x)             =>
          Stream.eval(Logger[F].error(x)("Error notifying the ODB").as(NullEvent))
      }

    override def stream(p: Stream[F, EventType[F]])(
      s0: EngineState[F]
    ): Stream[F, (EventResult[SeqEvent], EngineState[F])] =
      executeEngine.process(iterateQueues)(p)(s0)

    override def stopObserve(
      q:        EventQueue[F],
      seqId:    Observation.Id,
      observer: Observer,
      user:     UserDetails,
      graceful: Boolean
    ): F[Unit] = Applicative[F].unit
//      setObserver(q, seqId, user, observer) *>
//        q.offer(Event.modifyState[F, EngineState[F], SeqEvent](setObsCmd(seqId, StopGracefully)))
//          .whenA(graceful) *>
//        q.offer(
//          Event.actionStop[F, EngineState[F], SeqEvent](seqId,
//                                                        translators.stopObserve(seqId, graceful)
//          )
//        )
//
    override def abortObserve(
      q:        EventQueue[F],
      seqId:    Observation.Id,
      observer: Observer,
      user:     UserDetails
    ): F[Unit] = Applicative[F].unit
//      setObserver(q, seqId, user, observer) *>
//        q.offer(
//          Event.actionStop[F, EngineState[F], SeqEvent](seqId, translators.abortObserve(seqId))
//        )
//
    override def pauseObserve(
      q:        EventQueue[F],
      seqId:    Observation.Id,
      observer: Observer,
      user:     UserDetails,
      graceful: Boolean
    ): F[Unit] = Applicative[F].unit
//      setObserver(q, seqId, user, observer) *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](setObsCmd(seqId, PauseGracefully))
//        ).whenA(graceful) *>
//        q.offer(
//          Event.actionStop[F, EngineState[F], SeqEvent](seqId,
//                                                        translators.pauseObserve(seqId, graceful)
//          )
//        )
//
    override def resumeObserve(
      q:        EventQueue[F],
      seqId:    Observation.Id,
      observer: Observer,
      user:     UserDetails
    ): F[Unit] = Applicative[F].unit
//      q.offer(Event.modifyState[F, EngineState[F], SeqEvent](clearObsCmd(seqId))) *>
//        setObserver(q, seqId, user, observer) *>
//        q.offer(Event.getState[F, EngineState[F], SeqEvent](translators.resumePaused(seqId)))
//
    private def queueO(qid: QueueId): Optional[EngineState[F], ExecutionQueue] =
      Focus[EngineState[F]](_.queues).andThen(mapIndex[QueueId, ExecutionQueue].index(qid))

//    private def cmdStateO(qid: QueueId): Optional[EngineState[F], BatchCommandState] =
//      queueO(qid).andThen(ExecutionQueue.cmdState)
//
//    private def addSeqs(
//      qid:    QueueId,
//      seqIds: List[Observation.Id]
//    ): HandlerType[F, List[(Observation.Id, StepId)]] =
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
//                  (_, List.empty[(Observation.Id, StepId)])
//                }).toHandle
//              case (BatchCommandState.Run(o, u, c), _) =>
//                executeEngine.get.flatMap(st2 =>
//                  runSequences(shouldSchedule(qid, seqs.toSet)(st2), o, u, c)
//                )
//              case _                                   => executeEngine.pure(List.empty[(Observation.Id, StepId)])
//            })
//        ).getOrElse(executeEngine.pure(List.empty[(Observation.Id, StepId)]))
//      }
//
    override def addSequencesToQueue(
      q:      EventQueue[F],
      qid:    QueueId,
      seqIds: List[Observation.Id]
    ): F[Unit] = Applicative[F].unit
//    q.offer(
//      Event.modifyState[F, EngineState[F], SeqEvent](
//        addSeqs(qid, seqIds)
//          .as[SeqEvent](UpdateQueueAdd(qid, seqIds))
//      )
//    )
//
    override def addSequenceToQueue(
      q:     EventQueue[F],
      qid:   QueueId,
      seqId: Observation.Id
    ): F[Unit] = Applicative[F].unit
//      addSequencesToQueue(q, qid, List(seqId))
//
//    private def removeSeq(
//      qid:   QueueId,
//      seqId: Observation.Id
//    ): HandlerType[F, List[(Observation.Id, StepId)]] =
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
//                executeEngine.pure(List.empty[(Observation.Id, StepId)])
//              // If removed sequence was halting the queue, then removing it frees resources to run the next sequences
//              case (BatchCommandState.Run(o, u, c), _) =>
//                shouldSchedule(qid, Set(seqId))(st).isEmpty.fold(
//                  executeEngine.pure(List.empty[(Observation.Id, StepId)]),
//                  st.sequences
//                    .get(seqId)
//                    .map(x => runNextsInQueue(qid, o, u, c, x.seqGen.resources))
//                    .getOrElse(executeEngine.pure(List.empty[(Observation.Id, StepId)]))
//                )
//              case _                                   => executeEngine.pure(List.empty[(Observation.Id, StepId)])
//            })
//        ).getOrElse(executeEngine.pure(List.empty[(Observation.Id, StepId)]))
//      }

    override def removeSequenceFromQueue(
      q:     EventQueue[F],
      qid:   QueueId,
      seqId: Observation.Id
    ): F[Unit] = Applicative[F].unit
//    q.offer(
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
      q:     EventQueue[F],
      qid:   QueueId,
      seqId: Observation.Id,
      delta: Int,
      cid:   ClientId
    ): F[Unit] = Applicative[F].unit
//    q.offer(
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

    override def clearQueue(q: EventQueue[F], qid: QueueId): F[Unit]                    = q.offer(
      Event.modifyState[F, EngineState[F], SeqEvent](
        clearQ(qid).withEvent(UpdateQueueClear(qid)).toHandle
      )
    )
//
//    private def setObserverAndSelect(
//      sid:      Observation.Id,
//      observer: Observer,
//      user:     UserDetails,
//      clientId: ClientId
//    ): HandlerType[F, Unit] = Handle(
//      StateT[F, EngineState[F], (Unit, Option[Stream[F, EventType[F]]])] { st: EngineState[F] =>
//        EngineState
//          .sequences[F]
//          .andThen(mapIndex[Observation.Id, SequenceData[F]].index(sid))
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
//
//    private def runSequences(
//      ss:       Set[Observation.Id],
//      observer: Observer,
//      user:     UserDetails,
//      clientId: ClientId
//    ): HandlerType[F, List[(Observation.Id, StepId)]] =
//      ss.map(sid =>
//        setObserverAndSelect(sid, observer, user, clientId) *>
//          executeEngine.start(sid).reversedStreamFlatMap(_ => sequenceStart(sid))
//      ).toList
//        .sequence
//        .map(_.collect { case Some((sid, stepId)) => (sid, stepId) })
//
//    /**
//     * runQueue starts the queue. It founds the top eligible sequences in the queue, and runs them.
//     */
//    private def runQueue(
//      qid:      QueueId,
//      observer: Observer,
//      user:     UserDetails,
//      clientId: ClientId
//    ): HandlerType[F, List[(Observation.Id, StepId)]] =
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
//      user:     UserDetails,
//      clientId: ClientId,
//      freed:    Set[Resource]
//    ): HandlerType[F, List[(Observation.Id, StepId)]] =
//      executeEngine.get
//        .map(nextRunnableObservations(qid, freed))
//        .flatMap(runSequences(_, observer, user, clientId))
//
    override def startQueue(
      q:        EventQueue[F],
      qid:      QueueId,
      observer: Observer,
      user:     UserDetails,
      clientId: ClientId
    ): F[Unit] = Applicative[F].unit
    //    q.offer(
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
    override def stopQueue(q: EventQueue[F], qid: QueueId, clientId: ClientId): F[Unit] =
      Applicative[F].unit
//      q.offer(
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
    private def iterateQueues
      : PartialFunction[SystemEvent,
                        Handle[F, EngineState[F], Event[F, EngineState[F], SeqEvent], Unit]
      ] = { case SystemEvent.Finished(sid) =>
      executeEngine.unit
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
//
//    private def configSystemCheck(sid: Observation.Id, sys: Resource)(
//      st:                              EngineState[F]
//    ): Boolean = {
//      // Resources used by running sequences
//      val used = resourcesInUse(st)
//
//      // Resources reserved by running queues, excluding `sid` to prevent self blocking
//      val reservedByQueues = resourcesReserved(EngineState.sequences[F].modify(_ - sid)(st))
//
//      !(used ++ reservedByQueues).contains(sys)
//    }
//
//    private def configSystemHandle(
//      sid:      Observation.Id,
//      stepId:   StepId,
//      sys:      Resource,
//      clientID: ClientId
//    ): HandlerType[F, SeqEvent] =
//      executeEngine.get.flatMap { st =>
//        if (configSystemCheck(sid, sys)(st)) {
//          st.sequences
//            .get(sid)
//            .flatMap(_.seqGen.configActionCoord(stepId, sys))
//            .map(c =>
//              executeEngine.startSingle(ActionCoords(sid, c)).map[SeqEvent] {
//                case EventResult.Outcome.Ok => StartSysConfig(sid, stepId, sys)
//                case _                      => NullSeqEvent
//              }
//            )
//            .getOrElse(executeEngine.pure(NullSeqEvent))
//        } else {
//          executeEngine.pure(ResourceBusy(sid, stepId, sys, clientID))
//        }
//      }
//
//    /**
//     * Triggers the application of a specific step configuration to a system
//     */
    override def configSystem(
      q:        EventQueue[F],
      sid:      Observation.Id,
      observer: Observer,
      user:     UserDetails,
      stepId:   StepId,
      sys:      Resource,
      clientID: ClientId
    ): F[Unit] = Applicative[F].unit
//      setObserver(q, sid, user, observer) *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            configSystemHandle(sid, stepId, sys, clientID)
//          )
//        )
//
    def notifyODB(
      i: (EventResult[SeqEvent], EngineState[F])
    ): F[(EventResult[SeqEvent], EngineState[F])] = i.pure[F]
//      (i match {
//        case (SystemUpdate(SystemEvent.Failed(id, _, e), _), st) =>
//          st.sequences
//            .get(id)
//            .map { seq =>
//              Logger[F].error(s"Error executing ${seq.name} due to $e") *>
//                seq.visitId
//                  .map(vid =>
//                    systems.odb
//                      .obsAbort(vid, Observation.Id(id, seq.name), e.msg)
//                      .ensure(
//                        ObserveFailure
//                          .Unexpected("Unable to send ObservationAborted message to ODB.")
//                      )(identity)
//                  )
//                  .getOrElse(Applicative[F].unit)
//            }
//            .getOrElse(Applicative[F].unit)
//        case (SystemUpdate(SystemEvent.Executed(id), _), st)     =>
//          st.sequences
//            .get(id)
//            .filter(_.seq.status === SequenceState.Idle)
//            .flatMap { seq =>
//              seq.visitId.map(vid =>
//                systems.odb
//                  .obsPause(vid, Observation.Id(id, seq.name), "Sequence paused by user")
//                  .void
//              )
//            }
//            .getOrElse(Applicative[F].unit)
//        case (SystemUpdate(SystemEvent.Finished(id), _), st)     =>
//          st.sequences
//            .get(id)
//            .flatMap { seq =>
//              seq.visitId.map(vid =>
//                systems.odb.sequenceEnd(vid, Observation.Id(id, seq.name)).void
//              )
//            }
//            .getOrElse(Applicative[F].unit)
//        case _                                                   => Applicative[F].unit
//      }).as(i)
//
//    /**
//     * Update some metrics based on the event types
//     */
    def updateMetrics(e: EventResult[SeqEvent], sequences: List[SequenceView]): F[Unit] = {
      def instrument(id: Observation.Id): Option[Instrument] =
        sequences.find(_.obsId === id).map(_.metadata.instrument)

      e match {
        // TODO Add metrics for more events
        case UserCommandResponse(ue, _, _) =>
          ue match {
            case UserEvent.Start(id, _, _) =>
              instrument(id).map(i => sm.startRunning[F](i).void).getOrElse(Sync[F].unit)
            case _                         => Sync[F].unit
          }
        case SystemUpdate(se, _)           =>
          se match {
            case _ => Sync[F].unit
          }
      }
    }
//
//    private def updateSequenceEndo(
//      seqId:  Observation.Id,
//      obsseq: SequenceData[F]
//    ): Endo[EngineState[F]] = st =>
//      executeEngine.update(
//        seqId,
//        toStepList(obsseq.seqGen,
//                   obsseq.overrides,
//                   HeaderExtraData(st.conditions,
//                                   st.operator,
//                                   obsseq.observer,
//                                   st.sequences.get(seqId).flatMap(_.visitId)
//                   )
//        )
//      )(st)
//
//    private def refreshSequence(id: Observation.Id): Endo[EngineState[F]] = (st: EngineState[F]) =>
//      st.sequences.get(id).map(obsseq => updateSequenceEndo(id, obsseq)).foldLeft(st) {
//        case (s, f) => f(s)
//      }
//
//    private def refreshSequences: Endo[EngineState[F]] = (st: EngineState[F]) =>
//      st.sequences.map { case (id, obsseq) => updateSequenceEndo(id, obsseq) }.foldLeft(st) {
//        case (s, f) => f(s)
//      }
//
    override def setTcsEnabled(
      q:       EventQueue[F],
      seqId:   Observation.Id,
      user:    UserDetails,
      enabled: Boolean
    ): F[Unit] = Applicative[F].unit
//      logDebugEvent(
//        q,
//        s"ObserveEngine: Setting TCS enabled flag to '$enabled' for sequence '$seqId' by ${user.username}"
//      ) *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            (EngineState
//              .sequences[F]
//              .andThen(mapIndex[Observation.Id, SequenceData[F]].index(seqId))
//              .modify(SequenceData.overrides.modify { x =>
//                if (enabled) x.enableTcs else x.disableTcs
//              }) >>>
//              refreshSequence(seqId)).withEvent(SetTcsEnabled(seqId, user.some, enabled)).toHandle
//          )
//        )
//
    override def setGcalEnabled(
      q:       EventQueue[F],
      seqId:   Observation.Id,
      user:    UserDetails,
      enabled: Boolean
    ): F[Unit] = Applicative[F].unit
//      logDebugEvent(
//        q,
//        s"ObserveEngine: Setting Gcal enabled flag to '$enabled' for sequence '$seqId' by ${user.username}"
//      ) *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            (EngineState
//              .sequences[F]
//              .andThen(mapIndex[Observation.Id, SequenceData[F]].index(seqId))
//              .modify(SequenceData.overrides.modify { x =>
//                if (enabled) x.enableGcal else x.disableGcal
//              }) >>>
//              refreshSequence(seqId)).withEvent(SetGcalEnabled(seqId, user.some, enabled)).toHandle
//          )
//        )
//
    override def setInstrumentEnabled(
      q:       EventQueue[F],
      seqId:   Observation.Id,
      user:    UserDetails,
      enabled: Boolean
    ): F[Unit] = Applicative[F].unit
//      logDebugEvent(
//        q,
//        s"ObserveEngine: Setting instrument enabled flag to '$enabled' for sequence '$seqId' by ${user.username}"
//      ) *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            (EngineState
//              .sequences[F]
//              .andThen(mapIndex[Observation.Id, SequenceData[F]].index(seqId))
//              .modify(SequenceData.overrides.modify { x =>
//                if (enabled) x.enableInstrument else x.disableInstrument
//              }) >>>
//              refreshSequence(seqId))
//              .withEvent(SetInstrumentEnabled(seqId, user.some, enabled))
//              .toHandle
//          )
//        )
//
    override def setDhsEnabled(
      q:       EventQueue[F],
      seqId:   Observation.Id,
      user:    UserDetails,
      enabled: Boolean
    ): F[Unit] = Applicative[F].unit
//      logDebugEvent(
//        q,
//        s"ObserveEngine: Setting DHS enabled flag to '$enabled' for sequence '$seqId' by ${user.username}"
//      ) *>
//        q.offer(
//          Event.modifyState[F, EngineState[F], SeqEvent](
//            (EngineState
//              .sequences[F]
//              .andThen(mapIndex[Observation.Id, SequenceData[F]].index(seqId))
//              .modify(SequenceData.overrides.modify { x =>
//                if (enabled) x.enableDhs else x.disableDhs
//              }) >>>
//              refreshSequence(seqId)).withEvent(SetDhsEnabled(seqId, user.some, enabled)).toHandle
//          )
//        )

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

  private def systemsBeingConfigured[F[_]](st: EngineState[F]): Set[Resource] =
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
  private def resourcesInUse[F[_]](st: EngineState[F]): Set[Resource] =
    observations(st)
      .mapFilter(s => s.seq.status.isRunning.option(s.seqGen.resources))
      .foldK ++
      systemsBeingConfigured(st)

  /**
   * Resources reserved by running queues.
   */
  private def resourcesReserved[F[_]](st: EngineState[F]): Set[Resource] = {
    def reserved(q: ExecutionQueue): Set[Resource] = q.queue.collect {
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
  private def nextRunnableObservations[F[_]](qid: QueueId, freed: Set[Resource])(
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
    val resFailed: Set[Resource] = st.queues
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
  private def shouldSchedule[F[_]](qid: QueueId, sids: Set[Observation.Id])(
    st: EngineState[F]
  ): Set[Observation.Id] =
    findRunnableObservations(qid)(st).intersect(sids)

  /**
   * Build Observe and setup epics
   */
  def build[F[_]: Async: Logger](
    site:          Site,
    systems:       Systems[F],
    conf:          ObserveEngineConfiguration,
    metrics:       ObserveMetrics
  )(using
    executeEngine: ExecEngineType[F]
  ): F[ObserveEngine[F]] =
    Ref.of[F, Conditions](Conditions.Default).flatMap { rc =>
      createTranslator(site, systems, rc)
        .map(new ObserveEngineImpl[F](systems, conf, metrics, _, rc))
    }

  private def modifyStateEvent[F[_]](
    v:   SeqEvent,
    svs: => SequencesQueue[SequenceView]
  ): Stream[F, ObserveEvent] =
    v match {
      case NullSeqEvent                       => Stream.empty
      case SetOperator(_, _)                  => Stream.emit(OperatorUpdated(svs))
      case SetObserver(_, _, _)               => Stream.emit(ObserverUpdated(svs))
      case SetTcsEnabled(_, _, _)             => Stream.emit(OverridesUpdated(svs))
      case SetGcalEnabled(_, _, _)            => Stream.emit(OverridesUpdated(svs))
      case SetInstrumentEnabled(_, _, _)      => Stream.emit(OverridesUpdated(svs))
      case SetDhsEnabled(_, _, _)             => Stream.emit(OverridesUpdated(svs))
      case AddLoadedSequence(i, s, _, c)      => Stream.emit(LoadSequenceUpdated(i, s, svs, c))
      case ClearLoadedSequences(_)            => Stream.emit(ClearLoadedSequencesUpdated(svs))
      case SetConditions(_, _)                => Stream.emit(ConditionsUpdated(svs))
      case SetImageQuality(_, _)              => Stream.emit(ConditionsUpdated(svs))
      case SetWaterVapor(_, _)                => Stream.emit(ConditionsUpdated(svs))
      case SetSkyBackground(_, _)             => Stream.emit(ConditionsUpdated(svs))
      case SetCloudCover(_, _)                => Stream.emit(ConditionsUpdated(svs))
      case LoadSequence(id)                   => Stream.emit(SequenceLoaded(id, svs))
      case UnloadSequence(id)                 => Stream.emit(SequenceUnloaded(id, svs))
      case NotifyUser(m, cid)                 => Stream.emit(UserNotification(m, cid))
      case RequestConfirmation(m, cid)        => Stream.emit(UserPromptNotification(m, cid))
      case UpdateQueueAdd(qid, seqs)          =>
        Stream.emit(QueueUpdated(QueueManipulationOp.AddedSeqs(qid, seqs), svs))
      case UpdateQueueRemove(qid, s, p, l)    =>
        Stream.emits(
          QueueUpdated(QueueManipulationOp.RemovedSeqs(qid, s, p), svs)
            +: l.map { case (sid, step) => ClientSequenceStart(sid, step, svs) }
        )
      case UpdateQueueMoved(qid, cid, oid, p) =>
        Stream.emit(QueueUpdated(QueueManipulationOp.Moved(qid, cid, oid, p), svs))
      case UpdateQueueClear(qid)              => Stream.emit(QueueUpdated(QueueManipulationOp.Clear(qid), svs))
      case StartQueue(qid, _, l)              =>
        Stream.emits(
          QueueUpdated(QueueManipulationOp.Started(qid), svs)
            +: l.map { case (oid, step) => ClientSequenceStart(oid, step, svs) }
        )
      case StopQueue(qid, _)                  => Stream.emit(QueueUpdated(QueueManipulationOp.Stopped(qid), svs))
      case StartSysConfig(oid, stepId, res)   =>
        Stream.emit(SingleActionEvent(SingleActionOp.Started(oid, stepId, res)))
      case SequenceStart(oid, stepId)         => Stream.emit(ClientSequenceStart(oid, stepId, svs))
      case SequencesStart(l)                  =>
        Stream.emits(l.map { case (oid, step) => ClientSequenceStart(oid, step, svs) })
      case Busy(id, cid)                      => Stream.emit(UserNotification(ResourceConflict(id), cid))
      case ResourceBusy(oid, sid, res, cid)   =>
        Stream.emit(UserNotification(SubsystemBusy(oid, sid, res), cid))
    }

  private def executionQueueViews[F[_]](
    st: EngineState[F]
  ): SortedMap[QueueId, ExecutionQueueView] =
    SortedMap(st.queues.map { case (qid, q) =>
      qid -> ExecutionQueueView(qid, q.name, q.cmdState, q.status(st), q.queue.map(_.obsId))
    }.toList: _*)

  private def viewSequence[F[_]](obsSeq: SequenceData[F]): SequenceView = {
    val st         = obsSeq.seq
    val seq        = st.toSequence
    val instrument = obsSeq.seqGen.instrument

    def resources(s: SequenceGen.StepGen[F]): List[Resource] = s match {
      case s: SequenceGen.PendingStepGen[F] => s.resources.toList
      case _                                => List.empty
    }
    def engineSteps(seq: Sequence[F]): List[Step]            =
      obsSeq.seqGen.steps.zip(seq.steps).map { case (a, b) =>
        StepsView
          .stepsView(instrument)
          .stepView(a,
                    b,
                    resources(a).mapFilter(x =>
                      obsSeq.seqGen
                        .configActionCoord(a.id, x)
                        .map(i => (x, obsSeq.seq.getSingleState(i).actionStatus))
                    ),
                    obsSeq.pendingObsCmd
          )
      } match {
        // The sequence could be empty
        case Nil => Nil
        // Find first Pending Step when no Step is Running and mark it as Running
        case steps
            if Sequence.State.isRunning(st) && steps.forall(_.status =!= StepState.Running) =>
          val (xs, ys) = splitWhere(steps)(_.status === StepState.Pending)
          xs ++ ys.headOption.map(Step.status.replace(StepState.Running)).toList ++ ys.tail
        case steps
            if st.status === SequenceState.Idle && steps.exists(_.status === StepState.Running) =>
          val (xs, ys) = splitWhere(steps)(_.status === StepState.Running)
          xs ++ ys.headOption.map(Step.status.replace(StepState.Paused)).toList ++ ys.tail
        case x   => x
      }

    // TODO: Implement willStopIn
    SequenceView(
      seq.id,
      SequenceMetadata(instrument, obsSeq.observer, obsSeq.seqGen.title),
      st.status,
      obsSeq.overrides,
      engineSteps(seq),
      None
    )
  }

  private def toObserveEvent[F[_]](
    ev:     EventResult[SeqEvent],
    qState: EngineState[F]
  ): Stream[F, ObserveEvent] = {
    val sequences = qState.sequences.view.values.map(viewSequence).toList
    // Building the view is a relatively expensive operation
    // By putting it into a def we only incur that cost if the message requires it
    def svs       =
      SequencesQueue(
        List(
          qState.selected.gmosSouth.map(x => Instrument.GmosS -> x.id),
          qState.selected.gmosNorth.map(x => Instrument.GmosN -> x.id)
        ).flattenOption.toMap,
        qState.conditions,
        qState.operator,
        executionQueueViews(qState),
        sequences
      )

    ev match {
      case UserCommandResponse(ue, _, uev) =>
        ue match {
          case UserEvent.Start(id, _, _)        =>
            val rs = sequences.find(_.obsId === id).flatMap(_.runningStep.flatMap(_.id))
            rs.foldMap(x => Stream.emit(ClientSequenceStart(id, x, svs)))
          case UserEvent.Pause(_, _)            => Stream.emit(SequencePauseRequested(svs))
          case UserEvent.CancelPause(id, _)     => Stream.emit(SequencePauseCanceled(id, svs))
          case UserEvent.Breakpoint(_, _, _, _) => Stream.emit(StepBreakpointChanged(svs))
          case UserEvent.SkipMark(_, _, _, _)   => Stream.emit(StepSkipMarkChanged(svs))
          case UserEvent.Poll(cid)              => Stream.emit(SequenceRefreshed(svs, cid))
          case UserEvent.GetState(_)            => Stream.empty
          case UserEvent.ModifyState(_)         => modifyStateEvent(uev.getOrElse(NullSeqEvent), svs)
          case UserEvent.ActionStop(_, _)       => Stream.emit(ActionStopRequested(svs))
          case UserEvent.LogDebug(_, _)         => Stream.empty
          case UserEvent.LogInfo(m, ts)         => Stream.emit(ServerLogMessage(ServerLogLevel.INFO, ts, m))
          case UserEvent.LogWarning(m, ts)      =>
            Stream.emit(ServerLogMessage(ServerLogLevel.WARN, ts, m))
          case UserEvent.LogError(m, ts)        =>
            Stream.emit(ServerLogMessage(ServerLogLevel.ERROR, ts, m))
          case UserEvent.ActionResume(_, _, _)  => Stream.emit(SequenceUpdated(svs))
        }
      case SystemUpdate(se, _)             =>
        se match {
          // TODO: Sequence completed event not emitted by engine.
          case SystemEvent.Completed(_, _, _, _)                                    => Stream.emit(SequenceUpdated(svs))
          case SystemEvent.StopCompleted(id, _, _, _)                               => Stream.emit(SequenceStopped(id, svs))
          case SystemEvent.Aborted(id, _, _, _)                                     => Stream.emit(SequenceAborted(id, svs))
          case SystemEvent.PartialResult(_, _, _, Partial(_: InternalPartialVal))   => Stream.empty
          case SystemEvent.PartialResult(i, s, _, Partial(ObsProgress(t, r, v)))    =>
            Stream.emit(ObservationProgressEvent(ObservationProgress(i, s, t, r.self, v)))
          case SystemEvent.PartialResult(i, s, _, Partial(NSProgress(t, r, v, u)))  =>
            Stream.emit(
              ObservationProgressEvent(NSObservationProgress(i, s, t, r.self, v, u))
            )
          case SystemEvent.PartialResult(_, _, _, Partial(FileIdAllocated(fileId))) =>
            Stream.emit(FileIdStepExecuted(fileId, svs))
          case SystemEvent.PartialResult(_, _, _, _)                                =>
            Stream.emit(SequenceUpdated(svs))
          case SystemEvent.Failed(id, _, _)                                         => Stream.emit(SequenceError(id, svs))
          case SystemEvent.Busy(id, clientId)                                       =>
            Stream.emit(UserNotification(ResourceConflict(id), clientId))
          case SystemEvent.Executed(s)                                              => Stream.emit(StepExecuted(s, svs))
          case SystemEvent.Executing(_)                                             => Stream.emit(SequenceUpdated(svs))
          case SystemEvent.Finished(_)                                              => Stream.emit(SequenceCompleted(svs))
          case SystemEvent.Null                                                     => Stream.empty
          case SystemEvent.Paused(id, _, _)                                         => Stream.emit(ExposurePaused(id, svs))
          case SystemEvent.BreakpointReached(id)                                    => Stream.emit(SequencePaused(id, svs))
          case SystemEvent.SingleRunCompleted(c, _)                                 =>
            Stream.emit(
              singleActionEvent[F, SingleActionOp.Completed](c,
                                                             qState,
                                                             SingleActionOp.Completed.apply(_, _, _)
              )
            )
          case SystemEvent.SingleRunFailed(c, r)                                    =>
            Stream.emit(
              singleActionEvent[F, SingleActionOp.Error](c,
                                                         qState,
                                                         SingleActionOp.Error.apply(_, _, _, r.msg)
              )
            )
        }
    }
  }

  private def singleActionEvent[F[_], S <: SingleActionOp](
    c:      ActionCoords,
    qState: EngineState[F],
    f:      (Observation.Id, StepId, Resource) => S
  ): ObserveEvent =
    qState.sequences
      .get(c.sid)
      .flatMap(_.seqGen.resourceAtCoords(c.actCoords))
      .map(res => SingleActionEvent(f(c.sid, c.actCoords.stepId, res)))
      .getOrElse(NullEvent)

}
