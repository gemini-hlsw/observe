// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.syntax.all.*
import lucuma.core.enums.Breakpoint
import lucuma.core.util.NewType
import monocle.Focus
import monocle.Iso
import monocle.Lens
import monocle.macros.GenLens
import observe.engine.Action.ActionState
import observe.model.StepId
import observe.model.StepState

/**
 * A list of `Executions` grouped by observation.
 */
case class Step[F[_]](
  id:         StepId,
  breakpoint: Breakpoint,
  skipped:    Step.Skipped,
  skipMark:   Step.SkipMark,
  executions: List[ParallelActions[F]]
)

object Step {

  object SkipMark extends NewType[Boolean]
  type SkipMark = SkipMark.Type
  object Skipped extends NewType[Boolean]
  type Skipped = Skipped.Type

  def isoBool: Iso[Breakpoint, Boolean] =
    Iso[Breakpoint, Boolean](_ === Breakpoint.Enabled)(b =>
      if (b) Breakpoint.Enabled else Breakpoint.Disabled
    )

  def breakpointL[F[_]]: Lens[Step[F], Boolean] =
    Focus[Step[F]](_.breakpoint).andThen(isoBool)

  def skippedL[F[_]]: Lens[Step[F], Boolean] =
    Focus[Step[F]](_.skipped).andThen(Skipped.value)

  def init[F[_]](id: StepId, executions: List[ParallelActions[F]]): Step[F] =
    Step(id = id,
         breakpoint = Breakpoint.Disabled,
         skipped = Skipped(false),
         skipMark = SkipMark(false),
         executions = executions
    )

  /**
   * Calculate the `Step` `Status` based on the underlying `Action`s.
   */
  private def status_[F[_]](step: Step[F]): StepState =
    if (step.skipped.value) StepState.Skipped
    else
      // Find an error in the Step
      step.executions
        .flatMap(_.toList)
        .find(Action.errored)
        .flatMap { x =>
          x.state.runState match {
            case ActionState.Failed(Result.Error(msg)) => msg.some
            case _                                     => None
            // Return error or continue with the rest of the checks
          }
        }
        .map[StepState](StepState.Failed.apply)
        .getOrElse(
          // All actions in this Step were completed successfully, or the Step is empty.
          if (step.executions.flatMap(_.toList).exists(Action.aborted)) StepState.Aborted
          else if (step.executions.flatMap(_.toList).forall(Action.completed)) StepState.Completed
          else if (step.executions.flatMap(_.toList).forall(_.state.runState.isIdle))
            StepState.Pending
          // Not all actions are completed or pending.
          else StepState.Running
        )

  extension [F[_]](s: Step[F]) {
    def status: StepState = Step.status_(s)
  }

  /**
   * Step Zipper. This structure is optimized for the actual `Step` execution.
   */
  case class Zipper[F[_]](
    id:         StepId,
    breakpoint: Breakpoint,
    skipMark:   SkipMark,
    pending:    List[ParallelActions[F]],
    focus:      Execution[F],
    done:       List[ParallelActions[F]],
    rolledback: (Execution[F], List[ParallelActions[F]])
  ) { self =>

    /**
     * Adds the `Current` `Execution` to the list of completed `Execution`s and makes the next
     * pending `Execution` the `Current` one.
     *
     * If there are still `Action`s that have not finished in `Current` or if there are no more
     * pending `Execution`s it returns `None`.
     */
    val next: Option[Zipper[F]] =
      pending match {
        case Nil           => None
        case exep :: exeps =>
          (Execution.currentify(exep), focus.uncurrentify).mapN((curr, exed) =>
            self.copy(pending = exeps, focus = curr, done = exed.prepend(done))
          )
      }

    def rollback: Zipper[F] =
      self.copy(pending = rolledback._2, focus = rolledback._1, done = Nil)

    /**
     * Obtain the resulting `Step` only if all `Execution`s have been completed. This is a special
     * way of *unzipping* a `Zipper`.
     */
    val uncurrentify: Option[Step[F]] =
      if (pending.isEmpty)
        focus.uncurrentify.map(x => Step(id, breakpoint, Skipped(false), skipMark, x.prepend(done)))
      else None

    /**
     * Unzip a `Zipper`. This creates a single `Step` with either completed `Exection`s or pending
     * `Execution`s.
     */
    val toStep: Step[F] =
      Step(
        id = id,
        breakpoint = breakpoint,
        skipped = Skipped(false),
        skipMark = skipMark,
        executions = done ++ focus.toParallelActionsList ++ pending
      )

    val skip: Step[F] = toStep.copy(skipped = Skipped(true))

    def update(executions: List[ParallelActions[F]]): Zipper[F] =
      Zipper
        .calcRolledback(executions)
        .map { case r @ (_, exes) =>
          // Changing `pending` allows to propagate changes to non executed `executions`, even if the step is running
          // Don't do it if the number of executions changes. In that case the update will only have an effect if
          // the step is (re)started.
          if (exes.length === done.length + pending.length)
            this.copy(pending = exes.takeRight(pending.length), rolledback = r)
          else this.copy(rolledback = r)
        }
        .getOrElse(this)

  }

  object Zipper {

    private def calcRolledback[F[_]](
      executions: List[ParallelActions[F]]
    ): Option[(Execution[F], List[ParallelActions[F]])] = executions match {
      case Nil         => None
      case exe :: exes =>
        Execution.currentify(exe).map((_, exes))
    }

    /**
     * Make a `Zipper` from a `Step` only if all the `Execution`s in the `Step` are pending. This is
     * a special way of *zipping* a `Step`.
     */
    def currentify[F[_]](step: Step[F]): Option[Zipper[F]] =
      calcRolledback(step.executions).map { case (x, exes) =>
        Zipper(
          step.id,
          step.breakpoint,
          step.skipMark,
          exes,
          x,
          Nil,
          (x, exes)
        )
      }

    def current[F[_]]: Lens[Zipper[F], Execution[F]] =
      GenLens[Zipper[F]](_.focus)

  }

}
