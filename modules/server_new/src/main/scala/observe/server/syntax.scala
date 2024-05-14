// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Applicative
import cats.ApplicativeThrow
import cats.Eq
import cats.Functor
import cats.MonadError
import cats.MonadThrow
import cats.data.*
import cats.syntax.all.*
import observe.engine
import observe.engine.*
import observe.engine.Result
import observe.model.*
import observe.model.Observation
import observe.model.enums.*

extension [F[_]: MonadThrow, A](s: EitherT[F, ObserveFailure, A])
  def liftF: F[A] =
    s.value.flatMap(_.liftTo[F])

extension [F[_], A, B](fa: EitherT[F, A, B])
  def widenRethrowT[T](using
    me: MonadError[F, T],
    at: A <:< T
  ): F[B] =
    fa.leftMap(at).rethrowT

extension [F[_]](q:                 ExecutionQueue)
  // This assumes that there is only one instance of e in l
  private def moveElement[T](l: List[T], e: T => Boolean, delta: Int)(using eq: Eq[T]): List[T] =
    (l.indexWhere(e), l.find(e)) match
      case (idx, Some(v)) if delta =!= 0 =>
        val (h, t) = l.filterNot(e).splitAt(idx + delta)
        (h :+ v) ++ t
      case _                             => l

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

import Handle.{toHandle => toHandleT}
extension [F[_]: Applicative, A](f: EngineState[F] => (EngineState[F], A)) {
  def toHandle: HandlerType[F, A] =
    StateT[F, EngineState[F], A](st => f(st).pure[F]).toHandleT[EventType[F]]
}

extension (r: Either[Throwable, Response])
  def toResult[F[_]]: Result = r.fold(
    e =>
      e match {
        case e: ObserveFailure => Result.Error(ObserveFailure.explain(e))
        case e: Throwable      =>
          Result.Error(ObserveFailure.explain(ObserveFailure.ObserveException(e)))
      },
    r => Result.OK(r)
  )

extension [F[_]: ApplicativeThrow](r: F[Result])
  def safeResult: F[Result] = r.recover {
    case e: ObserveFailure => Result.Error(ObserveFailure.explain(e))
    case e: Throwable      => Result.Error(ObserveFailure.explain(ObserveFailure.ObserveException(e)))
  }

extension [F[_]: ApplicativeThrow, A <: Response](x: F[A])
  def toAction(kind:                                 ActionType): Action[F] = fromF[F](kind, x.attempt.map(_.toResult))

extension [F[_]: Functor](x: F[ConfigResult[F]])
  def toAction(kind: ActionType): Action[F] =
    fromF[F](kind, x.map(r => Result.OK(Response.Configured(r.sys.resource))))
