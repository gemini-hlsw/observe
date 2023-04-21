// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.Applicative
import cats.effect.Sync
import cats.syntax.all.*
import fs2.Stream
import observe.engine.EventResult
import observe.model.Observation.Id
import observe.model.{
  ClientId,
  Conditions,
  Observer,
  Operator,
  QueueId,
  StepId,
  UserDetails,
  events
}
import observe.model.enums.{
  CloudCover,
  ImageQuality,
  Instrument,
  Resource,
  RunOverride,
  SkyBackground,
  WaterVapor
}
import observe.server.keywords.DhsClientDisabled
import observe.server.{EngineState, EventQueue, EventType, ObserveEngine, SeqEvent, Systems}
import org.typelevel.log4cats.Logger

class TestObserveEngine[F[_]: Sync: Logger] extends ObserveEngine[F] {
  override val systems: Systems[F] = Systems(
    new DhsClientDisabled[F]
  )

  override def sync(q: EventQueue[F], seqId: Id): F[Unit] = Applicative[F].unit

  override def start(
    q:           EventQueue[F],
    id:          Id,
    user:        UserDetails,
    observer:    Observer,
    clientId:    ClientId,
    runOverride: RunOverride
  ): F[Unit] = Applicative[F].unit

  override def startFrom(
    q:           EventQueue[F],
    id:          Id,
    observer:    Observer,
    stp:         StepId,
    clientId:    ClientId,
    runOverride: RunOverride
  ): F[Unit] = Applicative[F].unit

  override def requestPause(
    q:        EventQueue[F],
    id:       Id,
    observer: Observer,
    user:     UserDetails
  ): F[Unit] = Applicative[F].unit

  override def requestCancelPause(
    q:        EventQueue[F],
    id:       Id,
    observer: Observer,
    user:     UserDetails
  ): F[Unit] = Applicative[F].unit

  override def setBreakpoint(
    q:        EventQueue[F],
    seqId:    Id,
    user:     UserDetails,
    observer: Observer,
    stepId:   StepId,
    v:        Boolean
  ): F[Unit] = Applicative[F].unit

  override def setOperator(q: EventQueue[F], user: UserDetails, name: Operator): F[Unit] =
    Applicative[F].unit

  override def setObserver(
    q:     EventQueue[F],
    seqId: Id,
    user:  UserDetails,
    name:  Observer
  ): F[Unit] = Applicative[F].unit

  override def setTcsEnabled(
    q:       EventQueue[F],
    seqId:   Id,
    user:    UserDetails,
    enabled: Boolean
  ): F[Unit] = Applicative[F].unit

  override def setGcalEnabled(
    q:       EventQueue[F],
    seqId:   Id,
    user:    UserDetails,
    enabled: Boolean
  ): F[Unit] = Applicative[F].unit

  override def setInstrumentEnabled(
    q:       EventQueue[F],
    seqId:   Id,
    user:    UserDetails,
    enabled: Boolean
  ): F[Unit] = Applicative[F].unit

  override def setDhsEnabled(
    q:       EventQueue[F],
    seqId:   Id,
    user:    UserDetails,
    enabled: Boolean
  ): F[Unit] = Applicative[F].unit

  override def selectSequence(
    q:        EventQueue[F],
    i:        Instrument,
    sid:      Id,
    observer: Observer,
    user:     UserDetails,
    clientId: ClientId
  ): F[Unit] = Applicative[F].unit

  override def clearLoadedSequences(q: EventQueue[F], user: UserDetails): F[Unit] =
    Applicative[F].unit

  override def resetConditions(q: EventQueue[F]): F[Unit] = Applicative[F].unit

  override def setConditions(q: EventQueue[F], conditions: Conditions, user: UserDetails): F[Unit] =
    Applicative[F].unit

  override def setImageQuality(q: EventQueue[F], iq: ImageQuality, user: UserDetails): F[Unit] =
    Applicative[F].unit

  override def setWaterVapor(q: EventQueue[F], wv: WaterVapor, user: UserDetails): F[Unit] =
    Applicative[F].unit

  override def setSkyBackground(q: EventQueue[F], sb: SkyBackground, user: UserDetails): F[Unit] =
    Applicative[F].unit

  override def setCloudCover(q: EventQueue[F], cc: CloudCover, user: UserDetails): F[Unit] =
    Applicative[F].unit

  override def setSkipMark(
    q:        EventQueue[F],
    seqId:    Id,
    user:     UserDetails,
    observer: Observer,
    stepId:   StepId,
    v:        Boolean
  ): F[Unit] = Applicative[F].unit

  override def requestRefresh(q: EventQueue[F], clientId: ClientId): F[Unit] = Applicative[F].unit

  override def stopObserve(
    q:        EventQueue[F],
    seqId:    Id,
    observer: Observer,
    user:     UserDetails,
    graceful: Boolean
  ): F[Unit] = Applicative[F].unit

  override def abortObserve(
    q:        EventQueue[F],
    seqId:    Id,
    observer: Observer,
    user:     UserDetails
  ): F[Unit] = Applicative[F].unit

  override def pauseObserve(
    q:        EventQueue[F],
    seqId:    Id,
    observer: Observer,
    user:     UserDetails,
    graceful: Boolean
  ): F[Unit] = Applicative[F].unit

  override def resumeObserve(
    q:        EventQueue[F],
    seqId:    Id,
    observer: Observer,
    user:     UserDetails
  ): F[Unit] = Applicative[F].unit

  override def addSequencesToQueue(q: EventQueue[F], qid: QueueId, seqIds: List[Id]): F[Unit] =
    Applicative[F].unit

  override def addSequenceToQueue(q: EventQueue[F], qid: QueueId, seqId: Id): F[Unit] =
    Applicative[F].unit

  override def removeSequenceFromQueue(q: EventQueue[F], qid: QueueId, seqId: Id): F[Unit] =
    Applicative[F].unit

  override def moveSequenceInQueue(
    q:     EventQueue[F],
    qid:   QueueId,
    seqId: Id,
    delta: Int,
    cid:   ClientId
  ): F[Unit] = Applicative[F].unit

  override def clearQueue(q: EventQueue[F], qid: QueueId): F[Unit] = Applicative[F].unit

  override def startQueue(
    q:        EventQueue[F],
    qid:      QueueId,
    observer: Observer,
    user:     UserDetails,
    clientId: ClientId
  ): F[Unit] = Applicative[F].unit

  override def stopQueue(q: EventQueue[F], qid: QueueId, clientId: ClientId): F[Unit] =
    Applicative[F].unit

  override def configSystem(
    q:        EventQueue[F],
    sid:      Id,
    observer: Observer,
    user:     UserDetails,
    stepId:   StepId,
    sys:      Resource,
    clientID: ClientId
  ): F[Unit] = Applicative[F].unit

  override def eventStream(q: EventQueue[F]): fs2.Stream[F, events.ObserveEvent] = Stream.empty

  override def stream(p: fs2.Stream[F, EventType[F]])(
    s0:                  EngineState[F]
  ): fs2.Stream[F, (EventResult[SeqEvent], EngineState[F])] = Stream.empty
}

object TestObserveEngine {
  def build[F[_]: Sync: Logger]: F[TestObserveEngine[F]] = new TestObserveEngine[F].pure[F]
}
