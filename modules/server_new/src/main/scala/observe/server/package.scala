// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe

import cats.data.*
import cats.effect.IO
import cats.effect.std.Queue
import cats.syntax.all.*
import cats.{Applicative, ApplicativeThrow, Endo, Eq, Functor, MonadError, MonadThrow}
import clue.ErrorPolicy
import fs2.Stream
import monocle.function.At.*
import monocle.function.Index.*
import monocle.Focus
import monocle.{Lens, Optional}
import observe.engine.Result.PauseContext
import observe.engine.{Engine, Result, _}
import observe.model.enums.*
import observe.model.{Observation, _}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

package server {

  import lucuma.schemas.ObservationDB.Scalars.VisitId

  import scala.concurrent.duration.Duration

  final case class EngineState[F[_]](
    queues:     ExecutionQueues,
    selected:   Map[Instrument, Observation.Id],
    conditions: Conditions,
    operator:   Option[Operator],
    sequences:  Map[Observation.Id, SequenceData[F]]
  )

  object EngineState     {
    def default[F[_]]: EngineState[F] =
      EngineState[F](
        Map(CalibrationQueueId -> ExecutionQueue.init(CalibrationQueueName)),
        Map.empty,
        Conditions.Default,
        None,
        Map.empty
      )

    def instrumentLoadedL[F[_]](
      instrument: Instrument
    ): Lens[EngineState[F], Option[Observation.Id]] =
      Focus[EngineState[F]](_.selected).andThen(
        at[Map[Instrument, Observation.Id], Instrument, Option[Observation.Id]](instrument)
      )

    def atSequence[F[_]](sid: Observation.Id): Optional[EngineState[F], SequenceData[F]] =
      Focus[EngineState[F]](_.sequences)
        .andThen(mapIndex[Observation.Id, SequenceData[F]].index(sid))

    def sequenceStateIndex[F[_]](sid: Observation.Id): Optional[EngineState[F], Sequence.State[F]] =
      atSequence[F](sid).andThen(Focus[SequenceData[F]](_.seq))

    def engineState[F[_]]: Engine.State[F, EngineState[F]] = (sid: Observation.Id) =>
      EngineState.sequenceStateIndex(sid)

    implicit final class WithEventOps[F[_]](val f: Endo[EngineState[F]]) extends AnyVal {
      def withEvent(ev: SeqEvent): EngineState[F] => (EngineState[F], SeqEvent) = f >>> { (_, ev) }
    }
  }

  final case class HeaderExtraData(
    conditions: Conditions,
    operator:   Option[Operator],
    observer:   Option[Observer],
    visitId:    Option[VisitId]
  )
  object HeaderExtraData {
    val default: HeaderExtraData = HeaderExtraData(Conditions.Default, None, None, None)
  }

//  final case class ObserveContext[F[_]](
//    resumePaused: Duration => Stream[F, Result],
//    progress:     ElapsedTime => Stream[F, Result],
//    stopPaused:   Stream[F, Result],
//    abortPaused:  Stream[F, Result],
//    expTime:      Duration
//  ) extends PauseContext[F]

}

package object server {
  given DefaultErrorPolicy: ErrorPolicy.RaiseAlways.type = ErrorPolicy.RaiseAlways

  type ExecutionQueues = Map[QueueId, ExecutionQueue]

  // This is far from ideal but we'll address this in another refactoring
  private implicit def logger: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("observe-engine")

  // TODO move this out of being a global. This act as an anchor to the rest of the code
  given executeEngine: Engine[IO, EngineState[IO], SeqEvent] =
    new Engine[IO, EngineState[IO], SeqEvent](EngineState.engineState[IO])

  type EventQueue[F[_]] = Queue[F, EventType[F]]

  implicit class EitherTFailureOps[F[_]: MonadThrow, A](
    s: EitherT[F, ObserveFailure, A]
  ) {
    def liftF: F[A] =
      s.value.flatMap(_.liftTo[F])
  }

  extension [F[_], A, B](fa: EitherT[F, A, B]) {
    def widenRethrowT[T](using
      me: MonadError[F, T],
      at: A <:< T
    ): F[B] =
      fa.leftMap(at).rethrowT
  }

  // This assumes that there is only one instance of e in l
  private def moveElement[T](l: List[T], e: T, delta: Int)(using eq: Eq[T]): List[T] = {
    val idx = l.indexOf(e)

    if (delta === 0 || idx < 0) {
      l
    } else {
      val (h, t) = l.filterNot(_ === e).splitAt(idx + delta)
      (h :+ e) ++ t
    }
  }

  extension [F[_]](q: ExecutionQueue) {
    def status(st: EngineState[F]): BatchExecState = {
      val statuses: Seq[SequenceState] = q.queue
        .map(sid => st.sequences.get(sid))
        .collect { case Some(x) => x }
        .map(_.seq.status)

      q.cmdState match {
        case BatchCommandState.Idle         => BatchExecState.Idle
        case BatchCommandState.Run(_, _, _) =>
          if (statuses.forall(_.isCompleted)) BatchExecState.Completed
          else if (statuses.exists(_.isRunning)) BatchExecState.Running
          else BatchExecState.Waiting
        case BatchCommandState.Stop         =>
          if (statuses.exists(_.isRunning)) BatchExecState.Stopping
          else BatchExecState.Idle
      }
    }

    def addSeq(sid:    Observation.Id): ExecutionQueue       = q.copy(queue = q.queue :+ sid)
    def addSeqs(sids:  List[Observation.Id]): ExecutionQueue = q.copy(queue = q.queue ++ sids)
    def removeSeq(sid: Observation.Id): ExecutionQueue       = q.copy(queue = q.queue.filter(_ =!= sid))
    def moveSeq(sid: Observation.Id, delta: Int): ExecutionQueue =
      q.copy(queue = moveElement(q.queue, sid, delta))
    def clear: ExecutionQueue                                    = q.copy(queue = List.empty)
  }

  implicit final class ToHandle[F[_]: Applicative, A](f: EngineState[F] => (EngineState[F], A)) {
    import Handle.toHandle
    def toHandle: HandleType[F, A] =
      StateT[F, EngineState[F], A](st => f(st).pure[F]).toHandle
  }

//  def toStepList[F[_]](
//    seq:       SequenceGen[F],
//    overrides: SystemOverrides,
//    d:         HeaderExtraData
//  ): List[engine.Step[F]] =
//    seq.steps.map(StepGen.generate(_, overrides, d))

  // If f is true continue, otherwise fail
  def failUnlessM[F[_]: MonadThrow](f: F[Boolean], err: Exception): F[Unit] =
    f.flatMap {
      MonadError[F, Throwable].raiseError(err).unlessA
    }

  extension (r: Either[Throwable, Response]) {
    def toResult[F[_]]: Result = r.fold(
      e =>
        e match {
          case e: ObserveFailure => Result.Error(ObserveFailure.explain(e))
          case e: Throwable      =>
            Result.Error(ObserveFailure.explain(ObserveFailure.ObserveException(e)))
        },
      r => Result.OK(r)
    )
  }

  extension [F[_]: ApplicativeThrow](r: F[Result]) {
    def safeResult: F[Result] = r.recover {
      case e: ObserveFailure => Result.Error(ObserveFailure.explain(e))
      case e: Throwable      => Result.Error(ObserveFailure.explain(ObserveFailure.ObserveException(e)))
    }
  }

  def catchObsErrors[F[_]](t: Throwable)(using L: Logger[F]): Stream[F, Result] = t match {
    case e: ObserveFailure =>
      Stream.eval(L.error(e)(s"Observation error: ${ObserveFailure.explain(e)}")) *>
        Stream.emit(Result.Error(ObserveFailure.explain(e)))
    case e: Throwable      =>
      Stream.eval(L.error(e)(s"Observation error: ${e.getMessage}")) *>
        Stream.emit(Result.Error(ObserveFailure.explain(ObserveFailure.ObserveException(e))))
  }

//  implicit class ActionResponseToAction[F[_]: Functor: ApplicativeError[
//    *[_],
//    Throwable
//  ], A <: Response](val x: F[A]) {
//    def toAction(kind: ActionType): Action[F] = fromF[F](kind, x.attempt.map(_.toResult))
//  }
//
//extension [F[_]: Functor]( x: F[ConfigResult]) {
//    def toAction(kind: ActionType): Action[F] =
//      fromF[F](kind, x.map(r => Result.OK(Response.Configured(r.sys.resource))))
//  }

  // Some types defined to avoid repeating long type definitions everywhere
  type EventType[F[_]]      = Event[F, EngineState[F], SeqEvent]
  type HandleType[F[_], A]  = Handle[F, EngineState[F], EventType[F], A]
  type ExecEngineType[F[_]] = Engine[F, EngineState[F], SeqEvent]

  def overrideLogMessage[F[_]: Logger](systemName: String, op: String): F[Unit] =
    Logger[F].info(s"System $systemName overridden. Operation $op skipped.")

}
