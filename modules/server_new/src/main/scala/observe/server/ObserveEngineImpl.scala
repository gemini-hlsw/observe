// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Applicative
import cats.Endo
import cats.Functor
import cats.Monoid
import cats.MonoidK
import cats.data.NonEmptyList
import cats.effect.Async
import cats.effect.Ref
import cats.effect.syntax.all.*
import cats.syntax.all.*
import coulomb.policy.strict.given
import eu.timepit.refined.cats.given
import eu.timepit.refined.types.numeric.NonNegShort
import fs2.Stream
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.CloudExtinction
import lucuma.core.model.ConstraintSet
import lucuma.core.model.ImageQuality
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
import observe.model.UserPrompt.Discrepancy
import observe.model.UserPrompt.ObsConditionsCheckOverride
import observe.model.UserPrompt.SeqCheck
import observe.model.UserPrompt.TargetCheckOverride
import observe.model.config.*
import observe.model.enums.BatchExecState
import observe.model.enums.ObserveLogLevel
import observe.model.enums.PendingObserveCmd
import observe.model.enums.PendingObserveCmd.*
import observe.model.enums.Resource
import observe.model.enums.RunOverride
import observe.model.events.*
import observe.server.events.*
import observe.server.odb.OdbProxy
import org.typelevel.log4cats.Logger

import java.util.concurrent.TimeUnit
import scala.annotation.unused
import scala.collection.immutable.SortedMap
import scala.concurrent.duration.*

import SeqEvent.*
import ClientEvent.*

private class ObserveEngineImpl[F[_]: Async: Logger](
  executeEngine:                    Engine[F, EngineState[F], SeqEvent],
  override val systems:             Systems[F],
  @annotation.unused settings:      ObserveEngineConfiguration,
  translator:                       SeqTranslate[F],
  @annotation.unused conditionsRef: Ref[F, Conditions]
)(using Monoid[F[Unit]])
    extends ObserveEngine[F] {

  /**
   * Check if the resources to run a sequence are available
   * @return
   *   true if resources are available
   */
  private def checkResources(obsId: Observation.Id)(st: EngineState[F]): Boolean = {
    // Resources used by running sequences
    val used = ObserveEngine.resourcesInUse(st)

    // Resources that will be used by sequences in running queues
    val reservedByQueues = ObserveEngine.resourcesReserved(st)

    st.sequences
      .get(obsId)
      .exists(x =>
        x.seqGen.resources.intersect(used).isEmpty && (
          st.queues.values.filter(_.status(st).running).exists(_.queue.contains(obsId)) ||
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
    requested: CloudExtinction.Preset
  ): Boolean =
    actual.forall(_ <= requested.toCloudExtinction)

  private def checkImageQuality(
    actual:    Option[ImageQuality],
    requested: ImageQuality.Preset
  ): Boolean =
    actual.forall(_ <= requested.toImageQuality)

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
      .option(
        Discrepancy(
          actualObsConditions.ce.fold(UnknownStr)(_.label),
          reqCE.toCloudExtinction.label
        )
      )

    val iqCmp = (!checkImageQuality(actualObsConditions.iq, reqIQ))
      .option(
        Discrepancy(
          actualObsConditions.iq.fold(UnknownStr)(_.label),
          reqIQ.toImageQuality.label
        )
      )

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
          ObserveEngine.tryNewAtom(systems.odb, translator, executeEngine, id, atomType)
        )
      }
      .getOrElse(executeEngine.unit)
  }

  override def requestPause(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User
  ): F[Unit] = setObserver(obsId, user, observer) *>
    executeEngine.offer(Event.pause[F, EngineState[F], SeqEvent](obsId, user))

  override def requestCancelPause(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User
  ): F[Unit] = setObserver(obsId, user, observer) *>
    executeEngine.offer(Event.cancelPause[F, EngineState[F], SeqEvent](obsId, user))

  override def setBreakpoints(
    obsId:    Observation.Id,
    user:     User,
    observer: Observer,
    steps:    List[Step.Id],
    v:        Breakpoint
  ): F[Unit] =
    // Set the observer after the breakpoints are set to do optimistic updates on the UI
    executeEngine.offer(Event.breakpoints[F, EngineState[F], SeqEvent](obsId, user, steps, v)) *>
      setObserver(obsId, user, observer)

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
    obsId: Observation.Id,
    user:  User,
    name:  Observer
  ): F[Unit] = logDebugEvent(
    s"ObserveEngine: Setting Observer name to '$name' for sequence '$obsId' by ${user.displayName}"
  ) *>
    executeEngine.offer(
      Event.modifyState[F, EngineState[F], SeqEvent](
        (EngineState
          .atSequence(obsId)
          .modify(Focus[SequenceData[F]](_.observer).replace(name.some)) >>>
          refreshSequence(obsId)).withEvent(SetObserver(obsId, user.some, name)).toHandle
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
    obsId:    Observation.Id,
    observer: Observer,
    user:     User,
    clientId: ClientId
  ): F[Unit] =
    val author = s", by '${user.displayName}' on client ${clientId.value}."
    executeEngine.inject(
      // We want the acquisition sequence to reset whenever we load the observation.
      // TODO The next 2 could be done in parallel.
      systems.odb.resetAcquisition(obsId) >>
        systems.odb
          .read(obsId)
          .flatMap(translator.sequence)
          .attempt
          .flatMap(
            _.fold(
              e =>
                Logger[F]
                  .warn(e)(s"Error loading observation $obsId$author")
                  .as(
                    Event.pure(
                      SeqEvent.NotifyUser(
                        Notification.LoadingFailed(
                          obsId,
                          List(s"Error loading observation $obsId", e.getMessage)
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
                        .map(e => s"Error loading observation $obsId: ${e.getMessage}$author")
                        .getOrElse(s"Error loading observation $obsId")
                    )
                    .as(
                      Event.pure(
                        SeqEvent.NotifyUser(
                          Notification.LoadingFailed(
                            obsId,
                            List(
                              s"Error loading observation $obsId"
                            ) ++ err.headOption.toList.map(e => e.getMessage)
                          ),
                          clientId
                        )
                      )
                    )
                case (errs, Some(seq)) =>
                  errs.isEmpty
                    .fold(
                      Logger[F].warn(s"Loaded observation $obsId$author"),
                      Logger[F]
                        .warn:
                          s"Loaded observation $obsId with warnings: ${errs.mkString}$author"
                    )
                    .as(
                      Event.modifyState[F, EngineState[F], SeqEvent]({ (st: EngineState[F]) =>
                        val l = EngineState.instrumentLoaded[F](seq.instrument)
                        if (l.get(st).forall(s => executeEngine.canUnload(s.seq))) {
                          st.sequencesByInstrument
                            .get(seq.instrument)
                            .foldMap(_.cleanup) >> // End background obsEdit subscription
                            // Start new obsEdit subscription
                            mountOdbObsSubscription(obsId).map { cleanup =>
                              (st.sequences
                                 .get(obsId)
                                 .fold(
                                   ODBSequencesLoader
                                     .loadSequenceEndo(observer.some, seq, l, cleanup)
                                 )(_ => ODBSequencesLoader.reloadSequenceEndo(seq, l))(st),
                               LoadSequence(obsId)
                              )
                            }
                        } else {
                          (
                            st,
                            SeqEvent
                              .NotifyUser(
                                Notification.LoadingFailed(
                                  obsId,
                                  List(
                                    s"Error loading observation $obsId",
                                    s"A sequence is running on instrument ${seq.instrument}"
                                  )
                                ),
                                clientId
                              ): SeqEvent
                          ).pure[F]
                        }
                      }.toHandleF)
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

  override def setCloudExtinction(ce: CloudExtinction, user: User, clientId: ClientId): F[Unit] =
    logDebugEvent(s"ObserveEngine: Setting cloud cover to $ce", user, clientId) *>
      executeEngine.offer(
        Event.modifyState[F, EngineState[F], SeqEvent](
          (EngineState.conditions[F].andThen(Conditions.ce).replace(ce.some) >>> refreshSequences)
            .withEvent(SetCloudExtinction(ce, user.some))
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

  private def singleActionClientEvent[F[_]](
    c:            ActionCoords,
    qState:       EngineState[F],
    clientAction: ClientEvent.SingleActionState,
    errorMsg:     Option[String] = none
  ): Option[TargetedClientEvent] =
    qState.sequences
      .get(c.obsId)
      .flatMap(_.seqGen.resourceAtCoords(c.actCoords))
      .map(res => SingleActionEvent(c.obsId, c.actCoords.stepId, res, clientAction, errorMsg))

  private def viewSequence[F[_]](obsSeq: SequenceData[F]): SequenceView = {
    val st         = obsSeq.seq
    val seq        = st.toSequence
    val instrument = obsSeq.seqGen.instrument
    val seqType    = obsSeq.seqGen.nextAtom.sequenceType

    def splitWhere[A](l: List[A])(p: A => Boolean): (List[A], List[A]) =
      l.splitAt(l.indexWhere(p))

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
        // When waiting user prompt, the sequence is Running, but no steps are.
        case steps
            if (Sequence.State.isRunning(st) && !Sequence.State.isWaitingUserPrompt(st)) && steps
              .forall(_.status =!= StepState.Running) =>
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

  private def executionQueueViews[F[_]](
    st: EngineState[F]
  ): SortedMap[QueueId, ExecutionQueueView] =
    SortedMap(st.queues.map { case (qid, q) =>
      qid -> ExecutionQueueView(qid, q.name, q.cmdState, q.status(st), q.queue.map(_.obsId))
    }.toList*)

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
      case RequestConfirmation(c @ UserPrompt.ChecksOverride(_, _, _), cid)                   =>
        Stream.emit(ClientEvent.ChecksOverrideEvent(c).forClient(cid))
      // case RequestConfirmation(m, cid)        => Stream.emit(UserPromptNotification(m, cid))
      case StartSysConfig(oid, stepId, res)                                                   =>
        Stream.emit[F, TargetedClientEvent](
          SingleActionEvent(oid, stepId, res, ClientEvent.SingleActionState.Started, none)
        ) ++ buildObserveStateStream(svs, odbProxy)
      // case Busy(id, cid)                      => Stream.emit(UserNotification(ResourceConflict(id), cid))
      // case ResourceBusy(oid, sid, res, cid)   =>
      //   Stream.emit(UserNotification(SubsystemBusy(oid, sid, res), cid))
      // case NoMoreAtoms(_)                     => Stream.empty
      case NewAtomLoaded(obsId, sequenceType, atomId)                                         =>
        Stream.emit[F, TargetedClientEvent](ClientEvent.AtomLoaded(obsId, sequenceType, atomId)) ++
          buildObserveStateStream(svs, odbProxy)
      case AtomCompleted(obsId, sequenceType, _) if sequenceType === SequenceType.Acquisition =>
        Stream.emit[F, TargetedClientEvent](ClientEvent.AcquisitionPromptReached(obsId))
      case e if e.isModelUpdate                                                               =>
        buildObserveStateStream(svs, odbProxy)
      case _                                                                                  => Stream.empty

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
          case UserEvent.LogInfo(m, ts)           =>
            Stream.emit(LogEvent(LogMessage(ObserveLogLevel.Info, ts, m)))
          case UserEvent.LogWarning(m, ts)        =>
            Stream.emit(LogEvent(LogMessage(ObserveLogLevel.Warning, ts, m)))
          case UserEvent.LogError(m, ts)          =>
            Stream.emit(LogEvent(LogMessage(ObserveLogLevel.Error, ts, m)))
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
          case SystemEvent.SingleRunFailed(c, Result.Error(msg))                   =>
            Stream.emits(
              singleActionClientEvent(
                c,
                qState,
                ClientEvent.SingleActionState.Failed,
                msg.some
              ).toList
            ) ++
              Stream.emit(SequenceFailed(c.obsId, msg): TargetedClientEvent) ++
              buildObserveStateStream(svs, odbProxy)
          case SystemEvent.StepComplete(obsId)                                     =>
            Stream.emit(StepComplete(obsId): TargetedClientEvent) ++
              buildObserveStateStream(svs, odbProxy)
          case SystemEvent.SequencePaused(obsId)                                   =>
            Stream.emit(SequencePaused(obsId): TargetedClientEvent)
          case SystemEvent.BreakpointReached(obsId)                                =>
            Stream.emit(BreakpointReached(obsId): TargetedClientEvent) ++
              buildObserveStateStream(svs, odbProxy)
          case SystemEvent.SequenceComplete(obsId)                                 =>
            Stream.emit(SequenceComplete(obsId): TargetedClientEvent) ++
              buildObserveStateStream(svs, odbProxy)
          case SystemEvent.Failed(obsId, _, Result.Error(msg))                     =>
            Stream.emit(SequenceFailed(obsId, msg): TargetedClientEvent) ++
              buildObserveStateStream(svs, odbProxy)
          case e if e.isModelUpdate                                                =>
            buildObserveStateStream(svs, odbProxy)
          case _                                                                   => Stream.empty
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
          case Left(e)             =>
            Stream.eval:
              LogMessage
                .now(ObserveLogLevel.Error, s"Error notifying ODB: ${e.getMessage}")
                .map(LogEvent(_))

  override def stream(
    s0: EngineState[F]
  ): Stream[F, (EventResult[SeqEvent], EngineState[F])] =
    // TODO We are never using the process function. Consider removing the `process` method and just returning the stream.
    executeEngine.process(PartialFunction.empty)(s0)

  override def stopObserve(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User,
    graceful: Boolean
  ): F[Unit] = setObserver(obsId, user, observer) *>
    systems.odb.stepStop(obsId) *>
    executeEngine
      .offer(Event.modifyState[F, EngineState[F], SeqEvent](setObsCmd(obsId, StopGracefully)))
      .whenA(graceful) *>
    executeEngine.offer(
      Event.actionStop[F, EngineState[F], SeqEvent](
        obsId,
        translator.stopObserve(obsId, graceful)
      )
    )

  override def abortObserve(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User
  ): F[Unit] = setObserver(obsId, user, observer) *>
    executeEngine.offer(
      Event.actionStop[F, EngineState[F], SeqEvent](obsId, translator.abortObserve(obsId))
    )

  override def pauseObserve(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User,
    graceful: Boolean
  ): F[Unit] = setObserver(obsId, user, observer) *>
    executeEngine
      .offer(
        Event.modifyState[F, EngineState[F], SeqEvent](setObsCmd(obsId, PauseGracefully))
      )
      .whenA(graceful) *>
    executeEngine.offer(
      Event.actionStop[F, EngineState[F], SeqEvent](
        obsId,
        translator.pauseObserve(obsId, graceful)
      )
    )

  override def resumeObserve(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User
  ): F[Unit] =
    executeEngine.offer(Event.modifyState[F, EngineState[F], SeqEvent](clearObsCmd(obsId))) *>
      setObserver(obsId, user, observer) *>
      executeEngine.offer(
        Event.getState[F, EngineState[F], SeqEvent](translator.resumePaused(obsId))
      )

  private def queueO(qid: QueueId): Optional[EngineState[F], ExecutionQueue] =
    Focus[EngineState[F]](_.queues).andThen(mapIndex[QueueId, ExecutionQueue].index(qid))

//    private def cmdStateO(qid: QueueId): Optional[EngineState[F], BatchCommandState] =
//      queueO(qid).andThen(ExecutionQueue.cmdState)

//    private def addSeqs(
//      qid:    QueueId,
//      obsIds: List[Observation.Id]
//    ): HandlerType[F, List[(Observation.Id, ObserveStep.Id)]] =
//      executeEngine.get.flatMap { st =>
//        (
//          for {
//            q    <- st.queues.get(qid)
//            seqs <- obsIds
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
    obsIds: List[Observation.Id]
  ): F[Unit] = Applicative[F].unit
//    executeEngine.offer(
//      Event.modifyState[F, EngineState[F], SeqEvent](
//        addSeqs(qid, obsIds)
//          .as[SeqEvent](UpdateQueueAdd(qid, obsIds))
//      )
//    )
//
  override def addSequenceToQueue(
    qid:   QueueId,
    obsId: Observation.Id
  ): F[Unit] = Applicative[F].unit
//      addSequencesToQueue(q, qid, List(obsId))
//
//    private def removeSeq(
//      qid:   QueueId,
//      obsId: Observation.Id
//    ): HandlerType[F, List[(Observation.Id, ObserveStep.Id)]] =
//      executeEngine.get.flatMap { st =>
//        (
//          for {
//            q    <- st.queues.get(qid)
//            if q.queue.contains(obsId)
//            sstOp = st.sequences.get(obsId).map(_.seq.status)
//            if q.status(st) =!= BatchExecState.Running ||
//              sstOp.forall(sst => !sst.isRunning && !sst.isCompleted)
//          } yield executeEngine.modify(queueO(qid).modify(_.removeSeq(obsId))) *>
//            ((q.cmdState, q.status(st)) match {
//              case (_, BatchExecState.Completed)       =>
//                executeEngine.pure(List.empty[(Observation.Id, ObserveStep.Id)])
//              // If removed sequence was halting the queue, then removing it frees resources to run the next sequences
//              case (BatchCommandState.Run(o, u, c), _) =>
//                shouldSchedule(qid, Set(obsId))(st).isEmpty.fold(
//                  executeEngine.pure(List.empty[(Observation.Id, ObserveStep.Id)]),
//                  st.sequences
//                    .get(obsId)
//                    .map(x => runNextsInQueue(qid, o, u, c, x.seqGen.resources))
//                    .getOrElse(executeEngine.pure(List.empty[(Observation.Id, ObserveStep.Id)]))
//                )
//              case _                                   => executeEngine.pure(List.empty[(Observation.Id, ObserveStep.Id)])
//            })
//        ).getOrElse(executeEngine.pure(List.empty[(Observation.Id, ObserveStep.Id)]))
//      }

  override def removeSequenceFromQueue(
    qid:   QueueId,
    obsId: Observation.Id
  ): F[Unit] = Applicative[F].unit
//    executeEngine.offer(
//      Event.modifyState[F, EngineState[F], SeqEvent](
//        executeEngine.get.flatMap(st =>
//          removeSeq(qid, obsId)
//            .map(
//              UpdateQueueRemove(qid,
//                                List(obsId),
//                                st.queues.get(qid).map(_.queue.indexOf(obsId)).toList,
//                                _
//              )
//            )
//        )
//      )
//    )
//
//    private def moveSeq(qid: QueueId, obsId: Observation.Id, delta: Int): Endo[EngineState[F]] =
//      st =>
//        st.queues
//          .get(qid)
//          .filter(_.queue.contains(obsId))
//          .map { _ =>
//            queueO(qid).modify(_.moveSeq(obsId, delta))(st)
//          }
//          .getOrElse(st)
//
  override def moveSequenceInQueue(
    qid:   QueueId,
    obsId: Observation.Id,
    delta: Int,
    cid:   ClientId
  ): F[Unit] = Applicative[F].unit
//    executeEngine.offer(
//      Event.modifyState[F, EngineState[F], SeqEvent](
//        executeEngine.get.flatMap(_ =>
//          moveSeq(qid, obsId, delta).withEvent(UpdateQueueMoved(qid, cid, obsId, 0)).toHandle
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
  // private def engineEventsHook
  //   : PartialFunction[SystemEvent,
  //                     Handle[F, EngineState[F], Event[F, EngineState[F], SeqEvent], Unit]
  //   ] = { case SystemEvent.SequenceComplete(sid) =>
  //   executeEngine.liftF[Unit](systems.odb.sequenceEnd(sid).void)
  // }
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
    val used = ObserveEngine.resourcesInUse(st)

    // Resources reserved by running queues, excluding `sid` to prevent self blocking
//      val reservedByQueues = resourcesReserved(EngineState.sequences[F].modify(_ - sid)(st))
//
//      !(used ++ reservedByQueues).contains(sys)
    !used.contains(sys)
  }

  private def configSystemHandle(
    obsId:    Observation.Id,
    stepId:   Step.Id,
    sys:      Resource | Instrument,
    clientID: ClientId
  ): HandlerType[F, SeqEvent] =
    executeEngine.get.flatMap { st =>
      if (configSystemCheck(sys, st)) {
        st.sequences
          .get(obsId)
          .flatMap(_.seqGen.configActionCoord(stepId, sys))
          .map(c =>
            executeEngine.startSingle(ActionCoords(obsId, c)).map[SeqEvent] {
              case EventResult.Outcome.Ok => StartSysConfig(obsId, stepId, sys)
              case _                      => NullSeqEvent
            }
          )
          .getOrElse(executeEngine.pure(NullSeqEvent))
      } else {
        executeEngine.pure(ResourceBusy(obsId, stepId, sys, clientID))
      }
    }

  /**
   * Triggers the application of a specific step configuration to a system /
   */
  override def configSystem(
    obsId:    Observation.Id,
    observer: Observer,
    user:     User,
    stepId:   Step.Id,
    sys:      Resource | Instrument,
    clientID: ClientId
  ): F[Unit] = setObserver(obsId, user, observer) *>
    executeEngine.offer(
      Event.modifyState[F, EngineState[F], SeqEvent](
        configSystemHandle(obsId, stepId, sys, clientID)
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
    obsId:    Observation.Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit] =
    logDebugEvent(
      s"ObserveEngine: Setting $resource enabled flag to '$enabled' for sequence '$obsId'",
      user,
      clientId
    ) *>
      executeEngine.offer(
        Event.modifyState[F, EngineState[F], SeqEvent](
          (EngineState
            .atSequence(obsId)
            .modify(SequenceData.overrides.modify { x =>
              modify(enabled, x)
            }) >>>
            refreshSequence(obsId)).withEvent(event).toHandle
        )
      )
  override def setTcsEnabled(
    obsId:    Observation.Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit] =
    toggleOverride(Resource.TCS.label,
                   (enabled, x) => if (enabled.value) x.enableTcs else x.disableTcs,
                   SetTcsEnabled(obsId, user.some, enabled),
                   obsId,
                   user,
                   enabled,
                   clientId
    )

  override def setGcalEnabled(
    obsId:    Observation.Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit] =
    toggleOverride(Resource.Gcal.label,
                   (enabled, x) => if (enabled.value) x.enableGcal else x.disableGcal,
                   SetGcalEnabled(obsId, user.some, enabled),
                   obsId,
                   user,
                   enabled,
                   clientId
    )

  override def setInstrumentEnabled(
    obsId:    Observation.Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit] =
    toggleOverride(
      "Instrument",
      (enabled, x) => if (enabled.value) x.enableInstrument else x.disableInstrument,
      SetInstrumentEnabled(obsId, user.some, enabled),
      obsId,
      user,
      enabled,
      clientId
    )

  override def setDhsEnabled(
    obsId:    Observation.Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit] =
    toggleOverride(
      "DHS",
      (enabled, x) => if (enabled.value) x.enableDhs else x.disableDhs,
      SetDhsEnabled(obsId, user.some, enabled),
      obsId,
      user,
      enabled,
      clientId: ClientId
    )

  // Reloads the current atom if the observation is edited in ODB, only if the sequence is not running.
  // We want this to reload regenerated steps if the QA of their recent execution does not pass.
  // If the sequence is running, this signal is ignored and the atom will be reloaded at the end of current step anyway.
  private def processObsEditOdbSignal(obsId: Observation.Id): F[Unit] =
    Logger[F].debug(s"Observation [$obsId] was edited in ODB") >>
      executeEngine
        .offer:
          Event.modifyState:
            executeEngine.get
              .map(EngineState.atSequence(obsId).getOption(_))
              .flatMap: seq =>
                if seq.exists(x => !x.seq.status.isRunning) then
                  ObserveEngine.onAtomReload[F](systems.odb, translator)(
                    executeEngine,
                    obsId,
                    OnAtomReloadAction.NoAction
                  )
                else
                  Handle.pure[F, EngineState[F], Event[F, EngineState[F], SeqEvent], SeqEvent]:
                    NullSeqEvent

  // Subscribes to obsEdit changes in the ODB.
  private def mountOdbObsSubscription(obsId: Observation.Id): F[F[Unit]] =
    systems.odb
      .obsEditSubscription(obsId)
      .flatMap: obsEditSignal =>
        obsEditSignal
          .evalMap(_ => processObsEditOdbSignal(obsId))
          .compile
          .drain
          .background
          .void
      .allocated
      .map(_._2)

}
