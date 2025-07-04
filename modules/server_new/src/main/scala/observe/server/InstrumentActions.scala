// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.data.NonEmptyList
import cats.effect.Temporal
import fs2.Stream
import observe.model.ActionType
import observe.model.dhs.ImageFileId
import observe.server.engine.Action
import observe.server.engine.Action.ActionState
import observe.server.engine.ParallelActions
import observe.server.engine.Result
import org.typelevel.log4cats.Logger

/**
 * Algebra to generate actions for an observation. Most instruments behave the same but in some
 * cases we need to customize behavior. The two prime examples are: GPI A&C GMOS N&S In both cases
 * the InstrumentActions for the instrument can provide the correct behavior
 */
trait InstrumentActions[F[_]] {

  /**
   * Produce a progress stream for the given observe
   * @param env
   *   Properties of the observation
   */
  def observationProgressStream(
    env: ObserveEnvironment[F]
  ): Stream[F, Result]

  /**
   * Builds a list of actions to run while observing In most cases it is just a plain observe but
   * could be skipped or made more complex if needed. It should include the progress updates.
   * @param env
   *   Properties of the observation
   */
  def observeActions(
    env: ObserveEnvironment[F]
  ): List[ParallelActions[F]]

  /**
   * Indicates if we should run the initial observe actions e.g. requesting a file Id
   */
  def runInitialAction(stepType: StepType): Boolean
}

object InstrumentActions {

  /**
   * This is the default observe action, just a simple observe call
   */
  def defaultObserveActions[F[_]](
    observeResults: Stream[F, Result]
  ): List[ParallelActions[F]] =
    List(
      NonEmptyList.one(
        Action(ActionType.Observe, observeResults, Action.State(ActionState.Idle, Nil))
      )
    )

  private def launchObserve[F[_]](
    env:       ObserveEnvironment[F],
    doObserve: (ImageFileId, ObserveEnvironment[F]) => Stream[F, Result]
  ): Stream[F, Result] =
    Stream.eval(FileIdProvider.fileId(env)).flatMap { fileId =>
      Stream.emit(Result.Partial(FileIdAllocated(fileId))) ++
        doObserve(fileId, env)
    }

  /**
   * Default Actions for most instruments it basically delegates to ObserveActions
   */
  def defaultInstrumentActions[F[_]: Temporal: Logger]: InstrumentActions[F] =
    new InstrumentActions[F] {
      def observationProgressStream(
        env: ObserveEnvironment[F]
      ): Stream[F, Result] =
        ObserveActions.observationProgressStream(env)

      override def observeActions(
        env: ObserveEnvironment[F]
      ): List[ParallelActions[F]] =
        defaultObserveActions(
          observationProgressStream(env)
            .mergeHaltR(launchObserve(env, ObserveActions.stdObserve[F]))
            .handleErrorWith(catchObsErrors[F])
        )

      def runInitialAction(stepType: StepType): Boolean = true
    }

}
