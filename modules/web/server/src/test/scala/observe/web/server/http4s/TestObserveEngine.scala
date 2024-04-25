// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.Applicative
import cats.effect.Async
import cats.effect.Sync
import cats.syntax.all.*
import fs2.Stream
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.Observation
import lucuma.core.model.User
import lucuma.core.model.sequence.Step
import observe.engine.EventResult
import observe.model.*
import observe.model.Observation.Id
import observe.model.enums.*
import observe.server.EngineState
import observe.server.ObserveEngine
import observe.server.SeqEvent
import observe.server.Systems
import observe.server.events.TargetedClientEvent
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
    stp:         Step.Id,
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

  override def setBreakpoints(
    seqId:    Id,
    user:     User,
    observer: Observer,
    stepId:   List[Step.Id],
    v:        Breakpoint
  ): F[Unit] = Applicative[F].unit

  override def setOperator(user: User, name: Operator): F[Unit] =
    Applicative[F].unit

  override def setObserver(
    seqId: Id,
    user:  User,
    name:  Observer
  ): F[Unit] = Applicative[F].unit

  override def setTcsEnabled(
    seqId:    Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit] = Applicative[F].unit

  override def setGcalEnabled(
    seqId:    Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit] = Applicative[F].unit

  override def setInstrumentEnabled(
    seqId:    Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
  ): F[Unit] = Applicative[F].unit

  override def setDhsEnabled(
    seqId:    Id,
    user:     User,
    enabled:  SubsystemEnabled,
    clientId: ClientId
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

  override def setImageQuality(iq: ImageQuality, user: User, clientId: ClientId): F[Unit] =
    Applicative[F].unit

  override def setWaterVapor(wv: WaterVapor, user: User, clientId: ClientId): F[Unit] =
    Applicative[F].unit

  override def setSkyBackground(sb: SkyBackground, user: User, clientId: ClientId): F[Unit] =
    Applicative[F].unit

  override def setCloudExtinction(cc: CloudExtinction, user: User, clientId: ClientId): F[Unit] =
    Applicative[F].unit

  override def setSkipMark(
    seqId:    Id,
    user:     User,
    observer: Observer,
    stepId:   Step.Id,
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
    stepId:   Step.Id,
    sys:      Resource | Instrument,
    clientID: ClientId
  ): F[Unit] = Applicative[F].unit

  override def clientEventStream: fs2.Stream[F, TargetedClientEvent] = Stream.empty

  override def stream(
    s0: EngineState[F]
  ): fs2.Stream[F, (EventResult[SeqEvent], EngineState[F])] = Stream.empty

  override def loadNextAtom(
    id:       Observation.Id,
    user:     User,
    observer: Observer,
    clientId: ClientId,
    atomType: SequenceType,
    run:      Boolean
  ): F[Unit] = Applicative[F].unit
}

object TestObserveEngine {
  def build[F[_]: Async: Logger]: F[TestObserveEngine[F]] =
    Systems.dummy[F].map(TestObserveEngine[F](_))
}
