// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe

import cats.data.*
import cats.effect.IO
import cats.effect.std.Queue
import cats.syntax.all.*
import cats.{Applicative, ApplicativeThrow, Endo, Eq, MonadError, MonadThrow, Order}
import clue.ErrorPolicy
import fs2.Stream
import monocle.{Focus, Getter, Optional}
import monocle.syntax.all.*
import observe.engine.Result.PauseContext
import observe.engine.{Engine, Result, *}
import observe.model.enums.*
import observe.model.{Observation, *}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import lucuma.schemas.ObservationDB.Scalars.VisitId

import scala.concurrent.duration.FiniteDuration
import server.InstrumentSystem.ElapsedTime
import squants.Length
import squants.space.Angle

package object server {

  case class Selected[F[_]](
    gmosSouth: Option[SequenceData[F]],
    gmosNorth: Option[SequenceData[F]]
  )

  final case class EngineState[F[_]](
    queues:     ExecutionQueues,
    selected:   Selected[F],
    conditions: Conditions,
    operator:   Option[Operator]
  ) {
    lazy val sequences: Map[Observation.Id, SequenceData[F]] =
      List(selected.gmosNorth, selected.gmosSouth).flattenOption.map(x => x.id -> x).toMap
  }

  object EngineState     {
    def default[F[_]]: EngineState[F] =
      EngineState[F](
        Map(CalibrationQueueId -> ExecutionQueue.init(CalibrationQueueName)),
        Selected(none, none),
        Conditions.Default,
        None
      )

    def instrumentLoadedG[F[_]](
      instrument: Instrument
    ): Getter[EngineState[F], Option[Observation.Id]] = Getter { s =>
      instrument match {
        case Instrument.GmosN => s.selected.gmosNorth.map(_.id)
        case Instrument.GmosS => s.selected.gmosSouth.map(_.id)
        case _                => none
      }
    }

    def atSequence[F[_]](sid: Observation.Id): Optional[EngineState[F], SequenceData[F]] =
      Focus[EngineState[F]](_.selected)
        .andThen(
          Optional[Selected[F], SequenceData[F]] { s =>
            s.gmosNorth.find(_.id === sid).orElse(s.gmosSouth.find(_.id === sid))
          } { d => s =>
            if (s.gmosNorth.exists(_.id === sid)) s.focus(_.gmosNorth).replace(d.some)
            else if (s.gmosSouth.exists(_.id === sid)) s.focus(_.gmosSouth).replace(d.some)
            else s
          }
        )

    def sequenceStateIndex[F[_]](sid: Observation.Id): Optional[EngineState[F], Sequence.State[F]] =
      Optional[EngineState[F], Sequence.State[F]](s =>
        s.selected.gmosSouth
          .filter(_.id === sid)
          .orElse(s.selected.gmosNorth.filter(_.id === sid))
          .map(_.seq)
      )(ss =>
        es =>
          if (es.selected.gmosSouth.exists(_.id === sid))
            es.copy(selected =
              es.selected.copy(gmosSouth = es.selected.gmosSouth.map(_.copy(seq = ss)))
            )
          else if (es.selected.gmosNorth.exists(_.id === sid))
            es.copy(selected =
              es.selected.copy(gmosNorth = es.selected.gmosNorth.map(_.copy(seq = ss)))
            )
          else es
      )

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

  final case class ObserveContext[F[_]](
    resumePaused: FiniteDuration => Stream[F, Result],
    progress:     ElapsedTime => Stream[F, Result],
    stopPaused:   Stream[F, Result],
    abortPaused:  Stream[F, Result],
    expTime:      FiniteDuration
  ) extends PauseContext

  type ExecutionQueues = Map[QueueId, ExecutionQueue]

  // This is far from ideal but we'll address this in another refactoring
  private implicit def logger: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("observe-engine")

  // TODO move this out of being a global. This act as an anchor to the rest of the code
  given executeEngine: Engine[IO, EngineState[IO], SeqEvent] =
    new Engine[IO, EngineState[IO], SeqEvent](EngineState.engineState[IO])

  type EventQueue[F[_]] = Queue[F, EventType[F]]

  extension [F[_]: MonadThrow, A](
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
  private def moveElement[T](l: List[T], e: T => Boolean, delta: Int)(using eq: Eq[T]): List[T] =
    (l.indexWhere(e), l.find(e)) match
      case (idx, Some(v)) if delta =!= 0 =>
        val (h, t) = l.filterNot(e).splitAt(idx + delta)
        (h :+ v) ++ t
      case _                             => l

  extension [F[_]](q: ExecutionQueue) {
    def status(st: EngineState[F]): BatchExecState = {
      val statuses: Seq[SequenceState] = q.queue.map(_.state)

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

    def addSeq(s: ExecutionQueue.SequenceInQueue): ExecutionQueue = q.copy(queue = q.queue :+ s)
    def addSeqs(ss: List[ExecutionQueue.SequenceInQueue]): ExecutionQueue =
      q.copy(queue = q.queue ++ ss)
    def removeSeq(sid: Observation.Id): ExecutionQueue                    =
      q.copy(queue = q.queue.filter(_.obsId =!= sid))
    def moveSeq(sid: Observation.Id, delta: Int): ExecutionQueue          =
      q.copy(queue =
        moveElement(q.queue, (x: ExecutionQueue.SequenceInQueue) => x.obsId === sid, delta)
      )
    def clear: ExecutionQueue                                             = q.copy(queue = List.empty)
  }

  implicit final class ToHandle[F[_]: Applicative, A](f: EngineState[F] => (EngineState[F], A)) {
    import Handle.toHandle
    def toHandle: HandlerType[F, A] =
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
//extension [F[_]: Functor]( x: F[ConfigResult[F]]) {
//    def toAction(kind: ActionType): Action[F] =
//      fromF[F](kind, x.map(r => Result.OK(Response.Configured(r.sys.resource))))
//  }

  // Some types defined to avoid repeating long type definitions everywhere
  type EventType[F[_]]      = Event[F, EngineState[F], SeqEvent]
  type HandlerType[F[_], A] = Handle[F, EngineState[F], EventType[F], A]
  type ExecEngineType[F[_]] = Engine[F, EngineState[F], SeqEvent]

  def overrideLogMessage[F[_]: Logger](systemName: String, op: String): F[Unit] =
    Logger[F].info(s"System $systemName overridden. Operation $op skipped.")

  given DefaultErrorPolicy: ErrorPolicy.RaiseAlways.type = ErrorPolicy.RaiseAlways

  given Order[Length] = Order.by(_.value)
  given Order[Angle]  = Order.by(_.value)

}
