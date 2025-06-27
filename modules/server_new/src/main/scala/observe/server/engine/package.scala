// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.engine

import cats.Functor
import cats.Monad
import cats.MonadThrow
import cats.data.NonEmptyList
import cats.data.StateT
import cats.effect.MonadCancelThrow
import cats.syntax.functor.*
import fs2.Stream
import lucuma.core.model.Observation
import observe.model.ActionType
import observe.server.EngineState
import org.typelevel.log4cats.Logger

// Top level synonyms

/**
 * This represents an actual real-world action to be done in the underlying systems.
 */
def fromF[F[_]](kind: ActionType, t: F[Result]*): Action[F] =
  Action(
    kind = kind,
    gen = Stream.emits(t).flatMap(Stream.eval),
    state = Action.State(Action.ActionState.Idle, Nil)
  )

/**
 * `ParallelActions` is a group of `Action`s that need to be run in parallel without interruption. A
 * *sequential* `Execution` can be represented with an `Execution` with a single `Action`.
 */
type ParallelActions[F[_]] = NonEmptyList[Action[F]]

extension [F[_]](v: List[Action[F]]) {
  def prepend(ac: List[ParallelActions[F]]): List[ParallelActions[F]] =
    NonEmptyList.fromList(v).foldRight(ac)(_ :: _)
}

enum ReloadReason:
  case SequenceFlow, EditEvent

// Type defined to avoid repeating long type definitions everywhere
type EngineHandle[F[_], A] = Handle[F, EngineState[F], Event[F], A]

// Constructors for `Handle` but with the types specific to the `EngineHandle` type.
object EngineHandle {
  inline def pure[F[_]: MonadCancelThrow, A](a: A): EngineHandle[F, A] = Handle.pure(a)

  inline def fromStateT[F[_]: Functor, O](
    s: StateT[F, EngineState[F], O]
  ): EngineHandle[F, O] =
    Handle.fromStateT(s)

  inline def liftF[F[_]: MonadCancelThrow, A](f: F[A]): EngineHandle[F, A] = Handle.liftF(f)

  inline def unit[F[_]: MonadCancelThrow]: EngineHandle[F, Unit] = Handle.unit

  inline def modifyStateEmit[F[_]: MonadCancelThrow](
    f: EngineState[F] => F[(EngineState[F], Stream[F, Event[F]])]
  ): EngineHandle[F, Unit] =
    Handle.modifyStateEmit(f)

  inline def modifyStateEmitSingle[F[_]: MonadCancelThrow](
    f: EngineState[F] => F[(EngineState[F], Event[F])]
  ): EngineHandle[F, Unit] =
    Handle.modifyStateEmitSingle(f)

  inline def fromEventStream[F[_]: MonadThrow](
    f: EngineState[F] => Stream[F, Event[F]]
  ): EngineHandle[F, Unit] =
    Handle.fromEventStream(f)

  inline def fromEventStream[F[_]: MonadThrow](
    p: Stream[F, Event[F]]
  ): EngineHandle[F, Unit] =
    Handle.fromEventStream(p)

  inline def fromEvents[F[_]: MonadThrow](
    f: EngineState[F] => Seq[Event[F]]
  ): EngineHandle[F, Unit] =
    Handle.fromEvents(f)

  inline def fromEvents[F[_]: MonadThrow](es: Event[F]*): EngineHandle[F, Unit] =
    Handle.fromEvents(es*)

  inline def fromSingleEventF[F[_]: MonadCancelThrow](f: F[Event[F]]): EngineHandle[F, Unit] =
    Handle.fromSingleEventF(f)

  inline def fromSingleEvent[F[_]: MonadCancelThrow](e: Event[F]): EngineHandle[F, Unit] =
    Handle.fromSingleEvent(e)

  inline def getState[F[_]: MonadCancelThrow]: EngineHandle[F, EngineState[F]] = Handle.getState

  inline def inspectState[F[_]: MonadCancelThrow, A](f: EngineState[F] => A): EngineHandle[F, A] =
    Handle.inspectState(f)

  inline def modifyStateF[F[_]: MonadCancelThrow, A](
    f: EngineState[F] => F[(EngineState[F], A)]
  ): EngineHandle[F, A] = Handle.modifyStateF(f)

  inline def modifyState[F[_]: Monad, A](
    f: EngineState[F] => (EngineState[F], A)
  ): EngineHandle[F, A] =
    Handle.modifyState(f)

  inline def modifyState_[F[_]: Monad](f: EngineState[F] => EngineState[F]): EngineHandle[F, Unit] =
    Handle.modifyState_(f)

  def getSequenceState[F[_]: MonadCancelThrow](
    obsId: Observation.Id
  ): EngineHandle[F, Option[Sequence.State[F]]] =
    inspectState(EngineState.sequenceStateAt(obsId).getOption(_))

  def inspectSequenceState[F[_]: MonadCancelThrow, A](obsId: Observation.Id)(
    f: Sequence.State[F] => A
  ): EngineHandle[F, Option[A]] =
    getSequenceState(obsId).map(_.map(f))

  def modifySequenceState[F[_]: Monad](obsId: Observation.Id)(
    f: Sequence.State[F] => Sequence.State[F]
  ): EngineHandle[F, Unit] =
    modifyState_(EngineState.sequenceStateAt(obsId).modify(f))

  def replaceSequenceState[F[_]: Monad](obsId: Observation.Id)(
    s: Sequence.State[F]
  ): EngineHandle[F, Unit] =
    modifyState_(EngineState.sequenceStateAt(obsId).replace(s))

  def debug[F[_]: MonadCancelThrow: Logger](msg: String): EngineHandle[F, Unit] =
    liftF(Logger[F].debug(msg))

  def logError[F[_]: MonadCancelThrow: Logger](e: Throwable)(msg: String): EngineHandle[F, Unit] =
    liftF(Logger[F].error(e)(msg))

  // For debugging
  def printSequenceState[F[_]: MonadCancelThrow: Logger](
    obsId: Observation.Id
  ): EngineHandle[F, Unit] =
    inspectSequenceState(obsId): (qs: Sequence.State[F]) =>
      StateT.liftF(Logger[F].debug(s"$qs"))
    .void
}
