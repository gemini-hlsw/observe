// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.effect.IO
import cats.data.NonEmptyList
import cats.syntax.all.*
import eu.timepit.refined.types.numeric.PosLong

import java.util.UUID
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import lucuma.core.model.Observation as LObservation
import lucuma.core.model.sequence.Atom
import observe.model.{ActionType, ClientId, SequenceState, StepId, UserDetails}
import observe.engine.TestUtil.TestState
import observe.common.test.*

import scala.Function.const
import cats.data.OptionT

class SequenceSuite extends munit.CatsEffectSuite {

  private given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("observe-engine")

  private val seqId = LObservation.Id(PosLong.unsafeFrom(1))

  private val atomId = Atom.Id(UUID.fromString("ad387bf4-093d-11ee-be56-0242ac120002"))

  // All tests check the output of running a sequence against the expected sequence of updates.

  private val user            = UserDetails("telops", "Telops")
  private val executionEngine = Engine.build[IO, TestState, Unit](TestState)

  def simpleStep(id: StepId, breakpoint: Boolean): Step[IO] =
    Step
      .init(
        id = id,
        executions = List(
          NonEmptyList.of(action, action), // Execution
          NonEmptyList.one(action)         // Execution
        )
      )
      .copy(breakpoint = Step.BreakpointMark(breakpoint))

  def isFinished(status: SequenceState): Boolean = status match {
    case SequenceState.Idle      => true
    case SequenceState.Completed => true
    case SequenceState.Failed(_) => true
    case _                       => false
  }

  def runToCompletion(s0: TestState): IO[Option[TestState]] =
    for {
      eng <- executionEngine
      _   <-
        eng.offer(Event.start[IO, TestUtil.TestState, Unit](seqId, user, ClientId(UUID.randomUUID)))
      v   <- eng
               .process(PartialFunction.empty)(s0)
               .drop(1)
               .takeThrough(a => !isFinished(a._2.sequences(seqId).status))
               .compile
               .last
    } yield v.map(_._2)

  test("stop on breakpoints") {

    val qs0: TestState =
      TestState(
        sequences = Map(
          (seqId,
           Sequence.State.init(
             Sequence.sequence(seqId,
                               atomId,
                               List(simpleStep(stepId(1), breakpoint = false),
                                    simpleStep(stepId(2), breakpoint = true)
                               )
             )
           )
          )
        )
      )

    val qs1 = runToCompletion(qs0)

    (for {
      s <- OptionT(qs1)
      t <- OptionT.pure(s.sequences(seqId))
      r <- OptionT.pure(t match {
             case Sequence.State.Zipper(zipper, status, _) =>
               zipper.done.length === 1 && zipper.pending.isEmpty && status === SequenceState.Idle
             case _                                        => false
           })
    } yield r).value.map(_.getOrElse(fail("Sequence not found"))).assert
  }

  test("resume execution to completion after a breakpoint") {

    val qs0: TestState =
      TestState(
        sequences = Map(
          (seqId,
           Sequence.State.init(
             Sequence.sequence(id = seqId,
                               atomId,
                               steps = List(simpleStep(stepId(1), breakpoint = false),
                                            simpleStep(stepId(2), breakpoint = true),
                                            simpleStep(stepId(3), breakpoint = false)
                               )
             )
           )
          )
        )
      )

    val qs1 = runToCompletion(qs0)

    val c1: IO[Boolean] = (for {
      s <- OptionT(qs1)
      t <- OptionT.pure(s.sequences(seqId))
      r <- OptionT.pure(t match {
             case Sequence.State.Zipper(zipper, _, _) =>
               zipper.pending.nonEmpty
             case _                                   => false
           })
    } yield r).value.map(_.getOrElse(fail("Sequence not found")))

    val c2: IO[Boolean] = (for {
      qs2 <- OptionT(qs1)
      s   <- OptionT(runToCompletion(qs2))
      t   <- OptionT.pure(s.sequences(seqId))
      r   <- OptionT.pure(t match {
               case f @ Sequence.State.Final(_, status) =>
                 f.done.length === 3 && status === SequenceState.Completed
               case _                                   => false
             })
    } yield r: Boolean).value.map(_.getOrElse(fail("Sequence not found")))

    (c1, c2).mapN((a, b) => a && b).assert

  }

  // TODO: Share these fixtures with StepSpec
  private object DummyResult extends Result.RetVal
  private val result: Result              = Result.OK(DummyResult)
  private val action: Action[IO]          = fromF[IO](ActionType.Undefined, IO(result))
  private val completedAction: Action[IO] =
    action.copy(state = Action.State(Action.ActionState.Completed(DummyResult), Nil))

  def simpleStep2(
    pending: List[ParallelActions[IO]],
    focus:   Execution[IO],
    done:    List[NonEmptyList[Result]]
  ): Step.Zipper[IO] = {
    val rollback: (Execution[IO], List[ParallelActions[IO]]) = {
      val doneParallelActions: List[ParallelActions[IO]]  = done.map(_.map(const(action)))
      val focusParallelActions: List[ParallelActions[IO]] = focus.toParallelActionsList
      doneParallelActions ++ focusParallelActions ++ pending match {
        case Nil     => (Execution.empty, Nil)
        case x :: xs => (Execution(x.toList), xs)
      }
    }

    Step.Zipper(
      id = stepId(1),
      breakpoint = Step.BreakpointMark(false),
      Step.SkipMark(false),
      pending = pending,
      focus = focus,
      done = done.map(_.map { r =>
        val x = fromF[IO](ActionType.Observe, IO(r))
        x.copy(state = Execution.actionStateFromResult(r)(x.state))
      }),
      rolledback = rollback
    )

  }
  val stepz0: Step.Zipper[IO]             = simpleStep2(Nil, Execution.empty, Nil)
  val stepza0: Step.Zipper[IO]            = simpleStep2(List(NonEmptyList.one(action)), Execution.empty, Nil)
  val stepza1: Step.Zipper[IO]            =
    simpleStep2(List(NonEmptyList.one(action)), Execution(List(completedAction)), Nil)
  val stepzr0: Step.Zipper[IO]            = simpleStep2(Nil, Execution.empty, List(NonEmptyList.one(result)))
  val stepzr1: Step.Zipper[IO]            =
    simpleStep2(Nil, Execution(List(completedAction, completedAction)), Nil)
  val stepzr2: Step.Zipper[IO]            = simpleStep2(Nil,
                                             Execution(List(completedAction, completedAction)),
                                             List(NonEmptyList.one(result))
  )
  val stepzar0: Step.Zipper[IO]           = simpleStep2(Nil, Execution(List(completedAction, action)), Nil)
  val stepzar1: Step.Zipper[IO]           = simpleStep2(List(NonEmptyList.one(action)),
                                              Execution(List(completedAction, completedAction)),
                                              List(NonEmptyList.one(result))
  )

  def simpleSequenceZipper(focus: Step.Zipper[IO]): Sequence.Zipper[IO] =
    Sequence.Zipper(seqId, atomId.some, Nil, focus, Nil)
  val seqz0: Sequence.Zipper[IO]                                        = simpleSequenceZipper(stepz0)
  val seqza0: Sequence.Zipper[IO]                                       = simpleSequenceZipper(stepza0)
  val seqza1: Sequence.Zipper[IO]                                       = simpleSequenceZipper(stepza1)
  val seqzr0: Sequence.Zipper[IO]                                       = simpleSequenceZipper(stepzr0)
  val seqzr1: Sequence.Zipper[IO]                                       = simpleSequenceZipper(stepzr1)
  val seqzr2: Sequence.Zipper[IO]                                       = simpleSequenceZipper(stepzr2)
  val seqzar0: Sequence.Zipper[IO]                                      = simpleSequenceZipper(stepzar0)
  val seqzar1: Sequence.Zipper[IO]                                      = simpleSequenceZipper(stepzar1)

  test("next should be None when there are no more pending executions") {
    assert(seqz0.next.isEmpty)
    assert(seqza0.next.isEmpty)
    assert(seqza1.next.nonEmpty)
    assert(seqzr0.next.isEmpty)
    assert(seqzr1.next.isEmpty)
    assert(seqzr2.next.isEmpty)
    assert(seqzar0.next.isEmpty)
    assert(seqzar1.next.nonEmpty)
  }

  test("startSingle should mark a single Action as started") {
    val seq = Sequence.State.init(
      Sequence.sequence(id = seqId,
                        atomId,
                        steps = List(simpleStep(stepId(1), breakpoint = false),
                                     simpleStep(stepId(2), breakpoint = false)
                        )
      )
    )

    val c = ActionCoordsInSeq(stepId(1), ExecutionIndex(0), ActionIndex(1))

    assert(seq.startSingle(c).getSingleState(c) == Action.ActionState.Started)
  }

  test("startSingle should not start single Action from completed Step") {
    val seq1 = Sequence.State.init(
      Sequence.sequence(
        id = seqId,
        atomId,
        steps = List(
          Step.init(
            id = stepId(1),
            executions = List(
              NonEmptyList.of(completedAction, completedAction), // Execution
              NonEmptyList.one(completedAction)                  // Execution
            )
          ),
          Step.init(
            id = stepId(2),
            executions = List(
              NonEmptyList.of(action, action), // Execution
              NonEmptyList.one(action)         // Execution
            )
          )
        )
      )
    )
    val seq2 = Sequence.State.Final(
      Sequence.sequence(
        id = seqId,
        atomId,
        steps = List(
          Step.init(
            id = stepId(1),
            executions = List(
              NonEmptyList.of(completedAction, completedAction), // Execution
              NonEmptyList.one(completedAction)                  // Execution
            )
          )
        )
      ),
      SequenceState.Completed
    )
    val c1   = ActionCoordsInSeq(stepId(1), ExecutionIndex(0), ActionIndex(0))

    assert(seq1.startSingle(c1).getSingleState(c1).isIdle)
    assert(seq2.startSingle(c1).getSingleState(c1).isIdle)

  }

  test("failSingle should mark a single running Action as failed") {
    val c   = ActionCoordsInSeq(stepId(1), ExecutionIndex(0), ActionIndex(0))
    val seq = Sequence.State
      .init(
        Sequence.sequence(id = seqId,
                          atomId,
                          steps = List(
                            Step.init(
                              id = stepId(1),
                              executions = List(
                                NonEmptyList.of(action, action), // Execution
                                NonEmptyList.one(action)         // Execution
                              )
                            )
                          )
        )
      )
      .startSingle(c)
    val c2  = ActionCoordsInSeq(stepId(1), ExecutionIndex(1), ActionIndex(0))

    assert(seq.failSingle(c, Result.Error("")).getSingleState(c).errored)
    assert(seq.failSingle(c2, Result.Error("")).getSingleState(c2).isIdle)
  }

  test("failSingle should mark a single running Action as completed") {
    val c   = ActionCoordsInSeq(stepId(1), ExecutionIndex(0), ActionIndex(0))
    val seq = Sequence.State
      .init(
        Sequence.sequence(id = seqId,
                          atomId,
                          steps = List(
                            Step.init(
                              id = stepId(1),
                              executions = List(
                                NonEmptyList.of(action, action), // Execution
                                NonEmptyList.one(action)         // Execution
                              )
                            )
                          )
        )
      )
      .startSingle(c)

    assert(seq.completeSingle(c, DummyResult).getSingleState(c).completed)
  }

}
