// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.syntax.all.*
import lucuma.core.enums.Breakpoint
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Step
import monocle.Lens
import monocle.macros.GenLens
import observe.engine.Action.ActionState
import observe.engine.Result.RetVal
import observe.model.SequenceState

/**
 * A list of `Step`s grouped by target and instrument.
 */
case class Sequence[F[_]] private (
  id:     Observation.Id,
  atomId: Option[Atom.Id],
  steps:  List[EngineStep[F]]
)

object Sequence {

  def empty[F[_]](id: Observation.Id): Sequence[F] = Sequence(id, none, List.empty)
  def sequence[F[_]](
    id:     Observation.Id,
    atomId: Atom.Id,
    steps:  List[EngineStep[F]]
  ): Sequence[F] = Sequence(id, atomId.some, steps)

  /**
   * Sequence Zipper. This structure is optimized for the actual `Sequence` execution.
   */
  case class Zipper[F[_]](
    id:      Observation.Id,
    atomId:  Option[Atom.Id],
    pending: List[EngineStep[F]],
    focus:   EngineStep.Zipper[F],
    done:    List[EngineStep[F]]
  ) {

    /**
     * Runs the next execution. If the current `Step` is completed it adds the `StepZ` under focus
     * to the list of completed `Step`s and makes the next pending `Step` the current one.
     *
     * If there are still `Execution`s that have not finished in the current `Step` or if there are
     * no more pending `Step`s it returns `None`.
     *
     * It skips steps, but honoring breakpoints.
     */
    val next: Option[Zipper[F]] =
      focus.next match {
        // Step completed
        case None      =>
          pending match {
            case Nil             => None
            case stepp :: stepps =>
              (EngineStep.Zipper.currentify(stepp), focus.uncurrentify).mapN((curr, stepd) =>
                Zipper(
                  id,
                  atomId,
                  stepps,
                  curr,
                  done :+ stepd
                )
              )
          }
        // Current step ongoing
        case Some(stz) => Some(Zipper(id, atomId, pending, stz, done))
      }

    def rollback: Zipper[F] = this.copy(focus = focus.rollback)

    /**
     * Obtain the resulting `Sequence` only if all `Step`s have been completed. This is a special
     * way of *unzipping* a `Zipper`.
     */
    val uncurrentify: Option[Sequence[F]] =
      if (pending.isEmpty)
        focus.uncurrentify.map(x => Sequence(id, atomId, done :+ x))
      else None

    /**
     * Unzip a `Zipper`. This creates a single `Sequence` with either completed `Step`s or pending
     * `Step`s.
     */
    val toSequence: Sequence[F] =
      Sequence(id, atomId, done ++ List(focus.toStep) ++ pending)
  }

  object Zipper {

    /**
     * Make a `Zipper` from a `Sequence` only if all the `Step`s in the `Sequence` are pending. This
     * is a special way of *zipping* a `Sequence`.
     */
    def currentify[F[_]](seq: Sequence[F]): Option[Zipper[F]] =
      seq.steps match {
        case Nil           => None
        case step :: steps =>
          EngineStep.Zipper
            .currentify(step)
            .map(
              Zipper(seq.id, seq.atomId, steps, _, Nil)
            )
      }

    def zipper[F[_]](seq: Sequence[F]): Option[Zipper[F]] =
      separate(seq).flatMap { case (pending, done) =>
        pending match {
          case Nil     => None
          case s :: ss =>
            EngineStep.Zipper
              .currentify(s)
              .map(
                Zipper(seq.id, seq.atomId, ss, _, done)
              )
        }
      }

    // We would use MonadPlus' `separate` if we wanted to separate Actions or
    // Results, but here we want only Steps.
    private def separate[F[_]](
      seq: Sequence[F]
    ): Option[(List[EngineStep[F]], List[EngineStep[F]])] =
      seq.steps.foldLeftM[Option, (List[EngineStep[F]], List[EngineStep[F]])]((Nil, Nil))(
        (acc, step) =>
          if (step.status.isPending)
            acc.leftMap(_ :+ step).some
          else if (step.status.isFinished)
            acc.map(_ :+ step).some
          else none
      )

    def focus[F[_]]: Lens[Zipper[F], EngineStep.Zipper[F]] =
      GenLens[Zipper[F]](_.focus)

    def current[F[_]]: Lens[Zipper[F], Execution[F]] =
      focus.andThen(EngineStep.Zipper.current)

  }

  sealed trait State[F[_]] {

    /**
     * Returns a new `State` where the next pending `Step` is been made the current `Step` under
     * execution and the previous current `Step` is placed in the completed `Sequence`.
     *
     * If the current `Step` has `Execution`s not completed or there are no more pending `Step`s it
     * returns `None`.
     */
    val next: Option[State[F]]

    /**
     * Tells if we are at the last action of the current step
     */
    val isLastAction: Boolean

    val status: SequenceState

    val pending: List[EngineStep[F]]

    def rollback: State[F]

    def setBreakpoints(stepId: List[Step.Id], v: Breakpoint): State[F]

    def getCurrentBreakpoint: Boolean

    /**
     * Current Execution
     */
    val current: Execution[F]

    val currentStep: Option[EngineStep[F]]

    val done: List[EngineStep[F]]

    /**
     * Given an index of a current `Action` it replaces such `Action` with the `Result` and returns
     * the new modified `State`.
     *
     * If the index doesn't exist, the new `State` is returned unmodified.
     */
    def mark(i: Int)(r: Result): State[F]

    def start(i: Int): State[F]

    /**
     * Updates the steps executions. It preserves the number of steps.
     * @param stepDefs
     *   New executions.
     * @return
     *   Updated state
     */
    def update(stepDefs: List[List[ParallelActions[F]]]): State[F]

    /**
     * Unzip `State`. This creates a single `Sequence` with either completed `Step`s or pending
     * `Step`s.
     */
    val toSequence: Sequence[F]

    // Functions to handle single run of Actions
    def startSingle(c: ActionCoordsInSeq): State[F]

    def failSingle(c: ActionCoordsInSeq, err: Result.Error): State[F]

    def completeSingle[V <: RetVal](c: ActionCoordsInSeq, r: V): State[F]

    def getSingleState(c: ActionCoordsInSeq): ActionState

    def getSingleAction(c: ActionCoordsInSeq): Option[Action[F]]

    val getSingleActionStates: Map[ActionCoordsInSeq, ActionState]

    def clearSingles: State[F]

  }

  object State {

    def status[F[_]]: Lens[State[F], SequenceState] =
      // `State` doesn't provide `.copy`
      Lens[State[F], SequenceState](_.status)(s => {
        case Zipper(st, _, x) => Zipper(st, s, x)
        case Final(st, _)     => Final(st, s)
      })

    def isRunning[F[_]](st: State[F]): Boolean = st.status.isRunning

    def userStopRequested[F[_]](st: State[F]): Boolean = st.status.isUserStopRequested

    def anyStopRequested[F[_]](st: State[F]): Boolean = st.status match {
      case SequenceState.Running(u, i, _, _) => u || i
      case _                                 => false
    }

    def userStopSet[F[_]](v: Boolean): State[F] => State[F] = status.modify {
      case r @ SequenceState.Running(_, _, _, _) => r.copy(userStop = v)
      case r                                     => r
    }

    def internalStopSet[F[_]](v: Boolean): State[F] => State[F] = status.modify {
      case r @ SequenceState.Running(_, _, _, _) => r.copy(internalStop = v)
      case r                                     => r
    }

    /**
     * Initialize a `State` passing a `Sequence` of pending `Step`s.
     */
    // TODO: Make this function `apply`?
    def init[F[_]](q: Sequence[F]): State[F] =
      Sequence.Zipper
        .zipper[F](q)
        .map(Zipper(_, SequenceState.Idle, Map.empty))
        .getOrElse(Final(q, SequenceState.Idle))

    /**
     * Rebuilds the state of a sequence with a new steps definition, but preserving breakpoints and
     * skip marks The sequence must not be running.
     * @param steps
     *   New sequence definition
     * @param st
     *   Old sequence state
     * @return
     *   The new sequence state
     */
    def reload[F[_]](steps: List[EngineStep[F]], st: State[F]): State[F] =
      if (st.status.isRunning) st
      else {
        val oldSeq   = st.toSequence
        val updSteps = oldSeq.steps.zip(steps).map { case (o, n) =>
          n.copy(breakpoint = o.breakpoint)
        } ++ steps.drop(oldSeq.steps.length)
        init(oldSeq.copy(steps = updSteps))
      }

    /**
     * This is the `State` in Zipper mode, which means is under execution.
     */
    case class Zipper[F[_]](
      zipper:     Sequence.Zipper[F],
      status:     SequenceState,
      singleRuns: Map[ActionCoordsInSeq, ActionState]
    ) extends State[F] { self =>

      override val next: Option[State[F]] = zipper.next match {
        // Last execution
        case None    => zipper.uncurrentify.map(Final[F](_, status))
        case Some(x) => Zipper(x, status, singleRuns).some
      }

      override val isLastAction: Boolean =
        zipper.focus.pending.isEmpty

      /**
       * Current Execution
       */
      override val current: Execution[F] =
        // Queue
        zipper
          // Step
          .focus
          // Execution
          .focus

      override val currentStep: Option[EngineStep[F]] = zipper.focus.toStep.some

      override val pending: List[EngineStep[F]] = zipper.pending

      override def rollback: Zipper[F] = self.copy(zipper = zipper.rollback)

      override def setBreakpoints(stepId: List[Step.Id], v: Breakpoint): State[F] =
        self.copy(zipper =
          zipper.copy(
            pending = zipper.pending.map: s =>
              if stepId.contains_(s.id)
              then s.copy(breakpoint = v)
              else s,
            focus =
              if stepId.contains_(zipper.focus.id)
              then zipper.focus.copy(breakpoint = v)
              else zipper.focus
          )
        )

      override def getCurrentBreakpoint: Boolean =
        (zipper.focus.breakpoint === Breakpoint.Enabled) && zipper.focus.done.isEmpty

      override val done: List[EngineStep[F]] = zipper.done

      private val zipperL: Lens[Zipper[F], Sequence.Zipper[F]] =
        GenLens[Zipper[F]](_.zipper)

      override def mark(i: Int)(r: Result): State[F] = {
        val currentExecutionL: Lens[Zipper[F], Execution[F]] =
          zipperL.andThen(Sequence.Zipper.current)

        currentExecutionL.modify(_.mark(i)(r))(self)
      }

      override def start(i: Int): State[F] = {

        val currentExecutionL: Lens[Zipper[F], Execution[F]] =
          zipperL.andThen(Sequence.Zipper.current)

        currentExecutionL.modify(_.start(i))(self).clearSingles
      }

      // Some rules:
      // 1. Done steps cannot change.
      // 2. Running step cannot change `done` or `focus` executions
      // 3. Must preserve breakpoints and skip marks
      override def update(stepDefs: List[List[ParallelActions[F]]]): State[F] =
        stepDefs.drop(zipper.done.length) match {
          case t :: ts =>
            zipperL.modify(zp =>
              zp.copy(
                focus = zp.focus.update(t),
                pending = pending.zip(ts).map { case (step, exes) =>
                  step.copy(executions = exes)
                } ++ pending.drop(ts.length)
              )
            )(this)
          case _       => this
        }

      override val toSequence: Sequence[F] = zipper.toSequence

      override def startSingle(c: ActionCoordsInSeq): State[F] =
        if (zipper.done.exists(_.id === c.stepId))
          self
        else self.copy(singleRuns = singleRuns + (c -> ActionState.Started))

      override def failSingle(c: ActionCoordsInSeq, err: Result.Error): State[F] =
        if (getSingleState(c).started)
          self.copy(singleRuns = singleRuns + (c -> ActionState.Failed(err)))
        else
          self

      override def completeSingle[V <: RetVal](c: ActionCoordsInSeq, r: V): State[F] =
        if (getSingleState(c).started)
          self.copy(singleRuns = singleRuns + (c -> ActionState.Completed(r)))
        else
          self

      override def getSingleState(c: ActionCoordsInSeq): ActionState =
        singleRuns.getOrElse(c, ActionState.Idle)

      override val getSingleActionStates: Map[ActionCoordsInSeq, ActionState] = singleRuns

      override def getSingleAction(c: ActionCoordsInSeq): Option[Action[F]] =
        for {
          step <- toSequence.steps.find(_.id === c.stepId)
          exec <- step.executions.get(c.execIdx.value)
          act  <- exec.get(c.actIdx.value)
        } yield act

      override def clearSingles: State[F] = self.copy(singleRuns = Map.empty)
    }

    /**
     * `State`. This doesn't have any `Step` under execution, there are only completed `Step`s.
     */
    case class Final[F[_]](seq: Sequence[F], status: SequenceState) extends State[F] { self =>

      override val next: Option[State[F]] = None

      override val current: Execution[F] = Execution.empty

      override val isLastAction: Boolean = true

      override val currentStep: Option[EngineStep[F]] = none

      override val pending: List[EngineStep[F]] = Nil

      override def rollback: Final[F] = self

      override def setBreakpoints(stepId: List[Step.Id], v: Breakpoint): State[F] = self

      override def getCurrentBreakpoint: Boolean = false

      override val done: List[EngineStep[F]] = seq.steps

      override def mark(i: Int)(r: Result): State[F] = self

      override def start(i: Int): State[F] = self

      override def update(stepDefs: List[List[ParallelActions[F]]]): State[F] = self

      override val toSequence: Sequence[F] = seq

      override def startSingle(c: ActionCoordsInSeq): State[F] = self

      override def failSingle(c: ActionCoordsInSeq, err: Result.Error): State[F] = self

      override def completeSingle[V <: RetVal](c: ActionCoordsInSeq, r: V): State[F] = self

      override def getSingleState(c: ActionCoordsInSeq): ActionState = ActionState.Idle

      override val getSingleActionStates: Map[ActionCoordsInSeq, ActionState] = Map.empty

      override def getSingleAction(c: ActionCoordsInSeq): Option[Action[F]] = None

      override def clearSingles: State[F] = self
    }

  }

}
