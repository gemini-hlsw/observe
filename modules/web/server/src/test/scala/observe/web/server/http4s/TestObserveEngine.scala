// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.Applicative
import cats.effect.Async
import cats.effect.Sync
import cats.syntax.all.*
import fs2.Stream
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.User
import observe.engine.EventResult
import observe.model.Observation.Id
import observe.model.*
import observe.model.enums.*
import observe.server.EngineState
import observe.server.EventQueue
import observe.server.EventType
import observe.server.ObserveEngine
import observe.server.SeqEvent
import observe.server.Systems
import observe.server.keywords.DhsClientDisabled
import org.typelevel.log4cats.Logger

class TestObserveEngine[F[_]: Sync: Logger](sys: Systems[F]) extends ObserveEngine[F] {
  override val systems: Systems[F] = sys

  override def sync(seqId: Id): F[Unit] = Applicative[F].unit

  override def start(
    id:          Id,
    user:        User,
    observer:    Observer,
    clientId:    ClientId,
    runOverride: RunOverride
  ): F[Unit] = Applicative[F].unit

  override def startFrom(
    id:          Id,
    observer:    Observer,
    stp:         StepId,
    clientId:    ClientId,
    runOverride: RunOverride
  ): F[Unit] = Applicative[F].unit

  override def requestPause(
    id:       Id,
    observer: Observer,
    user:     User
  ): F[Unit] = Applicative[F].unit

  override def requestCancelPause(
    id:       Id,
    observer: Observer,
    user:     User
  ): F[Unit] = Applicative[F].unit

  override def setBreakpoint(
    seqId:    Id,
    user:     User,
    observer: Observer,
    stepId:   StepId,
    v:        Boolean
  ): F[Unit] = Applicative[F].unit

  override def setOperator(user: User, name: Operator): F[Unit] =
    Applicative[F].unit

  override def setObserver(
    seqId: Id,
    user:  User,
    name:  Observer
  ): F[Unit] = Applicative[F].unit

  override def setTcsEnabled(
    seqId:   Id,
    user:    User,
    enabled: Boolean
  ): F[Unit] = Applicative[F].unit

  override def setGcalEnabled(
    seqId:   Id,
    user:    User,
    enabled: Boolean
  ): F[Unit] = Applicative[F].unit

  override def setInstrumentEnabled(
    seqId:   Id,
    user:    User,
    enabled: Boolean
  ): F[Unit] = Applicative[F].unit

  override def setDhsEnabled(
    seqId:   Id,
    user:    User,
    enabled: Boolean
  ): F[Unit] = Applicative[F].unit

  override def selectSequence(
    i:        Instrument,
    sid:      Id,
    observer: Observer,
    user:     User,
    clientId: ClientId
  ): F[Unit] = Applicative[F].unit

  override def clearLoadedSequences(user: User): F[Unit] =
    Applicative[F].unit

  override def resetConditions: F[Unit] = Applicative[F].unit

  override def setConditions(conditions: Conditions, user: User): F[Unit] =
    Applicative[F].unit

  override def setImageQuality(iq: ImageQuality, user: User): F[Unit] =
    Applicative[F].unit

  override def setWaterVapor(wv: WaterVapor, user: User): F[Unit] =
    Applicative[F].unit

  override def setSkyBackground(sb: SkyBackground, user: User): F[Unit] =
    Applicative[F].unit

  override def setCloudExtinction(cc: CloudExtinction, user: User): F[Unit] =
    Applicative[F].unit

  override def setSkipMark(
    seqId:    Id,
    user:     User,
    observer: Observer,
    stepId:   StepId,
    v:        Boolean
  ): F[Unit] = Applicative[F].unit

  override def requestRefresh(clientId: ClientId): F[Unit] = Applicative[F].unit

  override def stopObserve(
    seqId:    Id,
    observer: Observer,
    user:     User,
    graceful: Boolean
  ): F[Unit] = Applicative[F].unit

  override def abortObserve(
    seqId:    Id,
    observer: Observer,
    user:     User
  ): F[Unit] = Applicative[F].unit

  override def pauseObserve(
    seqId:    Id,
    observer: Observer,
    user:     User,
    graceful: Boolean
  ): F[Unit] = Applicative[F].unit

  override def resumeObserve(
    seqId:    Id,
    observer: Observer,
    user:     User
  ): F[Unit] = Applicative[F].unit

  override def addSequencesToQueue(qid: QueueId, seqIds: List[Id]): F[Unit] =
    Applicative[F].unit

  override def addSequenceToQueue(qid: QueueId, seqId: Id): F[Unit] =
    Applicative[F].unit

  override def removeSequenceFromQueue(qid: QueueId, seqId: Id): F[Unit] =
    Applicative[F].unit

  override def moveSequenceInQueue(
    qid:   QueueId,
    seqId: Id,
    delta: Int,
    cid:   ClientId
  ): F[Unit] = Applicative[F].unit

  override def clearQueue(qid: QueueId): F[Unit] = Applicative[F].unit

  override def startQueue(
    qid:      QueueId,
    observer: Observer,
    user:     User,
    clientId: ClientId
  ): F[Unit] = Applicative[F].unit

  override def stopQueue(qid: QueueId, clientId: ClientId): F[Unit] =
    Applicative[F].unit

  override def configSystem(
    sid:      Id,
    observer: Observer,
    user:     User,
    stepId:   StepId,
    sys:      Resource,
    clientID: ClientId
  ): F[Unit] = Applicative[F].unit

  override def eventStream: fs2.Stream[F, events.ObserveEvent] = Stream.empty

  override def stream(
    s0: EngineState[F]
  ): fs2.Stream[F, (EventResult[SeqEvent], EngineState[F])] = Stream.empty
}

object TestObserveEngine {
  def build[F[_]: Async: Logger]: F[TestObserveEngine[F]] =
    Systems.dummy[F].map(TestObserveEngine[F](_))
}
