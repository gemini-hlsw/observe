// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.Functor
import cats.Monad
import cats.data.NonEmptyList
import cats.data.StateT
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

enum OnAtomReloadAction:
  case NoAction, StartNewAtom

// Type defined to avoid repeating long type definitions everywhere
type EngineHandle[F[_], A] = Handle[F, EngineState[F], Event[F], A]

// Constructors for `Handle` but with the types specific to the `EngineHandle` type.
object EngineHandle {
  inline def pure[F[_]: Monad, A](a: A): EngineHandle[F, A] = Handle.pure(a)

  inline def fromStateT[F[_]: Functor, O](
    s: StateT[F, EngineState[F], O]
  ): EngineHandle[F, O] =
    Handle.fromStateT(s)

  inline def liftF[F[_]: Monad, A](f: F[A]): EngineHandle[F, A] = Handle.liftF(f)

  inline def unit[F[_]: Monad]: EngineHandle[F, Unit] = Handle.unit

  inline def fromEventStream[F[_]: Monad](
    f: EngineState[F] => Stream[F, Event[F]]
  ): EngineHandle[F, Unit] =
    Handle.fromEventStream(f)

  inline def fromEventStream[F[_]: Monad](p: Stream[F, Event[F]]): EngineHandle[F, Unit] =
    Handle.fromEventStream(p)

  inline def fromSingleEventF[F[_]: Monad](f: F[Event[F]]): EngineHandle[F, Unit] =
    Handle.fromSingleEventF(f)

  inline def fromSingleEvent[F[_]: Monad](e: Event[F]): EngineHandle[F, Unit] =
    Handle.fromSingleEvent(e)

  inline def getState[F[_]: Monad]: EngineHandle[F, EngineState[F]] = Handle.getState

  inline def inspectState[F[_]: Monad, A](f: EngineState[F] => A): EngineHandle[F, A] =
    Handle.inspectState(f)

  inline def modifyStateF[F[_]: Monad, A](
    f: EngineState[F] => F[(EngineState[F], A)]
  ): EngineHandle[F, A] = Handle.modifyStateF(f)

  inline def modifyState[F[_]: Monad, A](
    f: EngineState[F] => (EngineState[F], A)
  ): EngineHandle[F, A] =
    Handle.modifyState(f)

  inline def modifyState_[F[_]: Monad](f: EngineState[F] => EngineState[F]): EngineHandle[F, Unit] =
    Handle.modifyState_(f)

  def getSequenceState[F[_]: Monad](
    obsId: Observation.Id
  ): EngineHandle[F, Option[Sequence.State[F]]] =
    inspectState(EngineState.sequenceStateAt(obsId).getOption(_))

  def inspectSequenceState[F[_]: Monad, A](obsId: Observation.Id)(
    f: Sequence.State[F] => A
  ): EngineHandle[F, Option[A]] =
    inspectState(EngineState.sequenceStateAt(obsId).getOption(_).map(f))

  def modifySequenceState[F[_]: Monad](obsId: Observation.Id)(
    f: Sequence.State[F] => Sequence.State[F]
  ): EngineHandle[F, Unit] =
    modifyState_(EngineState.sequenceStateAt(obsId).modify(f))

  def replaceSequenceState[F[_]: Monad](obsId: Observation.Id)(
    s: Sequence.State[F]
  ): EngineHandle[F, Unit] =
    modifyState_(EngineState.sequenceStateAt(obsId).replace(s))

  // For debugging
  def printSequenceState[F[_]: Monad: Logger](obsId: Observation.Id): EngineHandle[F, Unit] =
    inspectSequenceState(obsId): (qs: Sequence.State[F]) =>
      StateT.liftF(Logger[F].debug(s"$qs"))
    .void
}
