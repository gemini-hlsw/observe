// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.data.NonEmptyList
import fs2.Stream
import observe.model.ActionType

// Top level synonyms

/**
 * This represents an actual real-world action to be done in the underlying systems.
 */
def fromF[F[_]](kind: ActionType, t: F[Result]*): Action[F] =
  Action(kind = kind,
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
