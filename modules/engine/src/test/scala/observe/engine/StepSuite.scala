// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.Ref
import cats.implicits.*
import fs2.Stream
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument.GmosSouth
import lucuma.core.model.sequence.Atom
import munit.CatsEffectSuite
import observe.common.test.*
import observe.engine.EventResult.*
import observe.engine.SystemEvent.*
import observe.engine.TestUtil.TestState
import observe.model.ActionType
import observe.model.ClientId
import observe.model.SequenceState
import observe.model.StepState
import observe.model.enums.Resource
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID
import scala.Function.const
import scala.concurrent.duration.*

class StepSuite extends CatsEffectSuite {

  private given L: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("observe")

  private val seqId  = observationId(1)
  private val atomId = Atom.Id(UUID.fromString("ad387bf4-093d-11ee-be56-0242ac120002"))

  private val executionEngine = Engine.build[IO, TestState, Unit](
    TestState,
    (eng, obsId) => eng.startNewAtom(obsId),
    (eng, obsId) => eng.startNewAtom(obsId)
  )

  private object DummyResult extends Result.RetVal with Serializable
  private val result                      = Result.OK(DummyResult)
  private val failure                     = Result.Error("Dummy error")
  private val actionFailed                = fromF[IO](ActionType.Undefined, IO(failure))
    .copy(state = Action.State(Action.ActionState.Failed(failure), Nil))
  private val action: Action[IO]          = fromF[IO](ActionType.Undefined, IO(result))
  private val actionCompleted: Action[IO] =
    action.copy(state = Action.State(Action.ActionState.Completed(DummyResult), Nil))
  private val clientId: ClientId          = ClientId(UUID.randomUUID)

  def simpleStep(
    pending: List[ParallelActions[IO]],
    focus:   Execution[IO],
    done:    List[NonEmptyList[Result]]
  ): EngineStep.Zipper[IO] = {
    val rollback: (Execution[IO], List[ParallelActions[IO]]) = {
      val doneParallelActions: List[ParallelActions[IO]]  = done.map(_.map(const(action)))
      val focusParallelActions: List[ParallelActions[IO]] = focus.toParallelActionsList
      doneParallelActions ++ focusParallelActions ++ pending match {
        case Nil     => (Execution.empty, Nil)
        case x :: xs => (Execution(x.toList), xs)
      }
    }

    EngineStep.Zipper(
      id = stepId(1),
      breakpoint = Breakpoint.Disabled,
      pending = pending,
      focus = focus,
      done = done.map(_.map { r =>
        val x = fromF[IO](ActionType.Observe, IO(r))
        x.copy(state = Execution.actionStateFromResult(r)(x.state))
      }),
      rolledback = rollback
    )
  }

  val stepz0: EngineStep.Zipper[IO]   = simpleStep(Nil, Execution.empty, Nil)
  val stepza0: EngineStep.Zipper[IO]  =
    simpleStep(List(NonEmptyList.one(action)), Execution.empty, Nil)
  val stepza1: EngineStep.Zipper[IO]  =
    simpleStep(List(NonEmptyList.one(action)), Execution(List(actionCompleted)), Nil)
  val stepzr0: EngineStep.Zipper[IO]  =
    simpleStep(Nil, Execution.empty, List(NonEmptyList.one(result)))
  val stepzr1: EngineStep.Zipper[IO]  =
    simpleStep(Nil, Execution(List(actionCompleted, actionCompleted)), Nil)
  val stepzr2: EngineStep.Zipper[IO]  = simpleStep(Nil,
                                                  Execution(List(actionCompleted, actionCompleted)),
                                                  List(NonEmptyList.one(result))
  )
  val stepzar0: EngineStep.Zipper[IO] =
    simpleStep(Nil, Execution(List(actionCompleted, action)), Nil)
  val stepzar1: EngineStep.Zipper[IO] = simpleStep(
    List(NonEmptyList.one(action)),
    Execution(List(actionCompleted, actionCompleted)),
    List(NonEmptyList.one(result))
  )
  private val startEvent              = Event.start[IO, TestState, Unit](seqId, user, clientId)

  /**
   * Emulates TCS configuration in the real world.
   */
  val configureTcs: Action[IO] = fromF[IO](
    ActionType.Configure(Resource.TCS),
    for {
      _ <- L.info("System: Start TCS configuration")
      _ <- IO.sleep(new FiniteDuration(200, MILLISECONDS))
      _ <- L.info("System: Complete TCS configuration")
    } yield Result.OK(DummyResult)
  )

  /**
   * Emulates Instrument configuration in the real world.
   */
  val configureInst: Action[IO] = fromF[IO](
    ActionType.Configure(GmosSouth),
    for {
      _ <- L.info("System: Start Instrument configuration")
      _ <- IO.sleep(new FiniteDuration(150, MILLISECONDS))
      _ <- L.info("System: Complete Instrument configuration")
    } yield Result.OK(DummyResult)
  )

  /**
   * Emulates an observation in the real world.
   */
  val observe: Action[IO] = fromF[IO](
    ActionType.Observe,
    for {
      _ <- L.info("System: Start observation")
      _ <- IO.sleep(new FiniteDuration(200, MILLISECONDS))
      _ <- L.info("System: Complete observation")
    } yield Result.OK(DummyResult)
  )

  def error(errMsg: String): Action[IO] = fromF[IO](
    ActionType.Undefined,
    IO.sleep(new FiniteDuration(200, MILLISECONDS)) *>
      Result.Error(errMsg).pure[IO]
  )

  def aborted: Action[IO] = fromF[IO](ActionType.Undefined,
                                      IO.sleep(new FiniteDuration(200, MILLISECONDS)) *>
                                        Result.OKAborted(DummyResult).pure[IO]
  )

  def errorSet1(errMsg: String): (Ref[IO, Int], Action[IO]) = {
    val ref    = Ref.unsafe[IO, Int](0)
    val action = fromF[IO](ActionType.Undefined,
                           ref.update(_ + 1).as(Result.OK(DummyResult)),
                           Result.Error(errMsg).pure[IO],
                           ref.update(_ + 1).as(Result.OK(DummyResult))
    )
    (ref, action)
  }

  def errorSet2(errMsg: String): Action[IO] =
    fromF[IO](ActionType.Undefined, Result.Error(errMsg).pure[IO])

  def fatalError(errMsg: String): Action[IO] =
    fromF[IO](ActionType.Undefined, IO.raiseError(new RuntimeException(errMsg)))

  def triggerPause(eng: Engine[IO, TestState, Unit]): Action[IO] = fromF[IO](
    ActionType.Undefined,
    for {
      _ <- eng.offer(Event.pause(seqId, user))
      // There is not a distinct result for Pause because the Pause action is a
      // trick for testing but we don't need to support it in real life, the pause
      // input event is enough.
    } yield Result.OK(DummyResult)
  )

  def triggerStart(eng: Engine[IO, TestState, Unit]): Action[IO] = fromF[IO](
    ActionType.Undefined,
    for {
      _ <- eng.offer(Event.start(seqId, user, clientId))
      // Same case that the pause action
    } yield Result.OK(DummyResult)
  )

  def isFinished(status: SequenceState): Boolean = status match {
    case SequenceState.Idle      => true
    case SequenceState.Completed => true
    case SequenceState.Failed(_) => true
    case SequenceState.Aborted   => true
    case _                       => false
  }

  private def runToCompletioIO(s0: TestState) = for {
    eng <- executionEngine
    _   <- eng.offer(Event.start[IO, TestState, Unit](seqId, user, clientId))
  } yield eng
    .process(PartialFunction.empty)(s0)
    .drop(1)
    .takeThrough(a => !isFinished(a._2.sequences(seqId).status))
    .map(_._2)
    .compile

  def runToCompletionLastIO(s0: TestState): IO[Option[TestState]] =
    runToCompletioIO(s0).flatMap(_.last)

  def runToCompletionAllIO(s0: TestState): IO[List[TestState]] =
    runToCompletioIO(s0).flatMap(_.toList)

  // This test must have a simple step definition and the known sequence of updates that running that step creates.
  // The test will just run step and compare the output with the predefined sequence of updates.

  // The difficult part is to set the pause command to interrupts the step execution in the middle.
  test("pause should stop execution in response to a pause command") {
    def qs0(eng: Engine[IO, TestState, Unit]): TestState =
      TestState(
        sequences = Map(
          (seqId,
           Sequence.State.init(
             Sequence.sequence(
               id = seqId,
               atomId = atomId,
               steps = List(
                 EngineStep.init(
                   id = stepId(1),
                   executions = List(
                     NonEmptyList.of(configureTcs, configureInst, triggerPause(eng)), // Execution
                     NonEmptyList.one(observe)                                        // Execution
                   )
                 )
               )
             )
           )
          )
        )
      )

    def notFinished(v: (EventResult[Unit], TestState)): Boolean =
      !isFinished(v._2.sequences(seqId).status)

    val m = for {
      eng <- Stream.eval(executionEngine)
      _   <- Stream.eval(eng.offer(startEvent))
      u   <- eng
               .process(PartialFunction.empty)(qs0(eng))
               .drop(1)
               .takeThrough(notFinished)
               .map(_._2)
    } yield u.sequences(seqId)

    m.compile.last.map {
      _.exists {
        case Sequence.State.Zipper(zipper, status, _) =>
          (zipper.focus.toStep match {
            case EngineStep(_, _, List(ex1, ex2)) =>
              Execution(ex1.toList).results.length == 3 && Execution(ex2.toList).actions.length == 1
            case _                                => false
          }) && status === SequenceState.Idle
        case _                                        => false
      }
    }.assert

  }

  test(
    "resume execution from the non-running state in response to a resume command, rolling back a partially run step."
  ) {
    // Engine state with one idle sequence partially executed. One EngineStep completed, two to go.
    val qs0: TestState =
      TestState(
        sequences = Map(
          (seqId,
           Sequence.State.Zipper(
             Sequence.Zipper(
               id = observationId(1),
               atomId = atomId.some,
               pending = Nil,
               focus = EngineStep.Zipper(
                 id = stepId(2),
                 breakpoint = Breakpoint.Disabled,
                 pending = Nil,
                 focus = Execution(List(observe)),
                 done = List(NonEmptyList.of(actionCompleted, actionCompleted)),
                 rolledback =
                   (Execution(List(configureTcs, configureInst)), List(NonEmptyList.one(observe)))
               ),
               done = Nil
             ),
             SequenceState.Idle,
             Map.empty
           )
          )
        )
      )

    val qs1 =
      for {
        eng <- executionEngine
        _   <- eng.offer(startEvent)
        u   <- eng
                 .process(PartialFunction.empty)(qs0)
                 .take(1)
                 .compile
                 .last
      } yield u.flatMap(_._2.sequences.get(seqId))

    qs1.map {
      _.exists {
        case Sequence.State.Zipper(zipper, status, _) =>
          (zipper.focus.toStep match {
            case EngineStep(_, _, List(ex1, ex2)) =>
              Execution(ex1.toList).actions.length == 2 && Execution(ex2.toList).actions.length == 1
            case _                                => false
          }) && status.isRunning
        case _                                        => false
      }
    }.assert

  }

  test("cancel a pause request in response to a cancel pause command.") {
    val qs0: TestState =
      TestState(
        sequences = Map(
          (seqId,
           Sequence.State.Zipper(
             Sequence.Zipper(
               id = observationId(1),
               atomId = atomId.some,
               pending = Nil,
               focus = EngineStep.Zipper(
                 id = stepId(2),
                 breakpoint = Breakpoint.Disabled,
                 pending = Nil,
                 focus = Execution(List(observe)),
                 done = List(NonEmptyList.of(actionCompleted, actionCompleted)),
                 rolledback =
                   (Execution(List(configureTcs, configureInst)), List(NonEmptyList.one(observe)))
               ),
               done = Nil
             ),
             SequenceState.Running(userStop = true, internalStop = false, waitingNextAtom = false),
             Map.empty
           )
          )
        )
      )

    val qs1 = for {
      eng <- executionEngine
      _   <- eng.offer(Event.cancelPause[IO, TestState, Unit](seqId, user))
      v   <- eng
               .process(PartialFunction.empty)(qs0)
               .take(1)
               .compile
               .last
               .map(_.map(_._2))
    } yield v

    qs1
      .map(x =>
        x.flatMap(_.sequences.get(seqId)).exists {
          case Sequence.State.Zipper(_, status, _) =>
            status.isRunning
          case _                                   => false
        }
      )
      .assert

  }

  test("engine should test pause command if step is not being executed.") {
    val qs0: TestState =
      TestState(
        sequences = Map(
          (seqId,
           Sequence.State.init(
             Sequence.sequence(
               id = seqId,
               atomId = atomId,
               steps = List(
                 EngineStep.init(
                   id = stepId(1),
                   executions = List(
                     NonEmptyList.of(configureTcs, configureInst), // Execution
                     NonEmptyList.one(observe)                     // Execution
                   )
                 )
               )
             )
           )
          )
        )
      )
    val qss            = for {
      eng <- executionEngine
      _   <- eng.offer(Event.pause[IO, TestState, Unit](seqId, user))
      v   <- eng
               .process(PartialFunction.empty)(qs0)
               .take(1)
               .compile
               .last
               .map(_.map(_._2))
    } yield v

    qss.map { x =>
      x.flatMap(_.sequences.get(seqId)).exists {
        case Sequence.State.Zipper(zipper, status, _) =>
          zipper.focus.toStep.match {
            case EngineStep(_, _, List(ex1, ex2)) =>
              Execution(ex1.toList).actions.length == 2 && Execution(ex2.toList).actions.length == 1
            case _                                => false
          } && (status === SequenceState.Idle)
        case _                                        => false
      }
    }.assert
  }

  // Be careful that start command doesn't run an already running sequence.
  test("engine test start command if step is already running.") {
    def qs0(eng: Engine[IO, TestState, Unit]): TestState =
      TestState(
        sequences = Map(
          (seqId,
           Sequence.State.init(
             Sequence.sequence(
               id = seqId,
               atomId = atomId,
               steps = List(
                 EngineStep.init(
                   id = stepId(1),
                   executions = List(
                     NonEmptyList.of(configureTcs, configureInst), // Execution
                     NonEmptyList.one(triggerStart(eng)),          // Execution
                     NonEmptyList.one(observe)                     // Execution
                   )
                 )
               )
             )
           )
          )
        )
      )

    val qss = for {
      eng <- executionEngine
      _   <- eng.offer(Event.start(seqId, user, clientId))
      v   <- eng
               .process(PartialFunction.empty)(qs0(eng))
               .drop(1)
               .takeThrough(a => !isFinished(a._2.sequences(seqId).status))
               .compile
               .toVector
    } yield v

    qss.map { x =>
      val actionsCompleted = x.map(_._1).collect { case SystemUpdate(x: Completed[?], _) => x }
      assertEquals(actionsCompleted.length, 4)

      val executionsCompleted = x.map(_._1).collect { case SystemUpdate(x: Executed, _) => x }
      assertEquals(executionsCompleted.length, 3)

      val sequencesCompleted = x.map(_._1).collect { case SystemUpdate(x: Finished, _) => x }
      assertEquals(sequencesCompleted.length, 1)

      x.lastOption
        .flatMap(_._2.sequences.get(seqId))
        .exists {
          case Sequence.State.Final(_, status) =>
            status === SequenceState.Completed
          case _                               => false
        }
    }.assert
  }

  // For this test, one of the actions in the step must produce an error as result.
  test("engine should stop execution and propagate error when an Action ends in error.") {
    val errMsg         = "Dummy error"
    val qs0: TestState =
      TestState(
        sequences = Map(
          (seqId,
           Sequence.State.init(
             Sequence.sequence(
               id = seqId,
               atomId = atomId,
               steps = List(
                 EngineStep.init(
                   id = stepId(1),
                   executions = List(
                     NonEmptyList.of(configureTcs, configureInst), // Execution
                     NonEmptyList.one(error(errMsg)),
                     NonEmptyList.one(observe)
                   )
                 )
               )
             )
           )
          )
        )
      )

    val qs1 = runToCompletionLastIO(qs0)

    qs1.map { x =>
      x.flatMap(_.sequences.get(seqId)).exists {
        case Sequence.State.Zipper(zipper, status, _) =>
          (zipper.focus.toStep match {
            // Check that the sequence stopped midway
            case EngineStep(_, _, List(ex1, ex2, ex3)) =>
              Execution(ex1.toList).results.length == 2 && Execution(
                ex2.toList
              ).results.length == 1 && Execution(ex3.toList).actions.length == 1
            case _                                     => false
          }) && (status == SequenceState.Failed(errMsg)) // And that it ended in error
        case _                                        => false

      }
    }.assert
  }

  test(
    "engine should complete execution and propagate error when a partial Action ends in error."
  ) {
    val errMsg         = "Dummy error"
    val (ref, action)  = errorSet1(errMsg)
    val qs0: TestState =
      TestState(
        sequences = Map(
          (seqId,
           Sequence.State.init(
             Sequence.sequence(id = seqId,
                               atomId = atomId,
                               steps = List(
                                 EngineStep.init(
                                   id = stepId(1),
                                   executions = List(
                                     NonEmptyList.one(action)
                                   )
                                 )
                               )
             )
           )
          )
        )
      )

    val qs1 = runToCompletionLastIO(qs0)

    qs1.map { x =>
      x.flatMap(_.sequences.get(seqId)).exists {
        case Sequence.State.Final(_, status) =>
          status === SequenceState.Completed
        case _                               => false
      }
    }.assert *>
      ref.get.map(_ === 1).assert

  }

  test("engine should mark a step as aborted if the action ends as aborted") {
    val qs0: TestState =
      TestState(
        sequences = Map(
          (seqId,
           Sequence.State.init(
             Sequence.sequence(id = seqId,
                               atomId = atomId,
                               steps = List(
                                 EngineStep.init(
                                   id = stepId(1),
                                   executions = List(
                                     NonEmptyList.one(aborted)
                                   )
                                 )
                               )
             )
           )
          )
        )
      )

    val qs1 = runToCompletionLastIO(qs0)

    qs1.map { x =>
      x.flatMap(_.sequences.get(seqId)).exists {
        case Sequence.State.Zipper(_, status, _) =>
          // And that it ended in aborted
          status === SequenceState.Aborted
        case _                                   => false
      }
    }.assert
  }

  test("engine should stop execution and propagate error when a single partial Action fails") {
    val errMsg         = "Dummy error"
    val qs0: TestState =
      TestState(
        sequences = Map(
          (seqId,
           Sequence.State.init(
             Sequence.sequence(id = seqId,
                               atomId = atomId,
                               steps = List(
                                 EngineStep.init(
                                   id = stepId(1),
                                   executions = List(
                                     NonEmptyList.one(errorSet2(errMsg))
                                   )
                                 )
                               )
             )
           )
          )
        )
      )

    val qs1 = runToCompletionLastIO(qs0)

    qs1.map { x =>
      x.flatMap(_.sequences.get(seqId)).exists {
        case Sequence.State.Zipper(_, status, _) =>
          // Without the error we should have a value 2
          // And that it ended in error
          status === SequenceState.Failed(errMsg)
        case _                                   => false
      }
    }.assert
  }

  test("engine should let fatal errors bubble") {
    val errMsg         = "Dummy error"
    val qs0: TestState =
      TestState(
        sequences = Map(
          (seqId,
           Sequence.State.init(
             Sequence.sequence(id = seqId,
                               atomId = atomId,
                               steps = List(
                                 EngineStep.init(
                                   id = stepId(1),
                                   executions = List(
                                     NonEmptyList.one(fatalError(errMsg))
                                   )
                                 )
                               )
             )
           )
          )
        )
      )

    interceptIO[RuntimeException](runToCompletionLastIO(qs0))

  }

  test("engine should record a partial result and continue execution.") {

    // For result types
    case class RetValDouble(v: Double)     extends Result.RetVal
    case class PartialValDouble(v: Double) extends Result.PartialVal

    val qs0: TestState =
      TestState(
        sequences = Map(
          (seqId,
           Sequence.State.init(
             Sequence.sequence(
               id = seqId,
               atomId = atomId,
               steps = List(
                 EngineStep.init(
                   id = stepId(1),
                   executions = List(
                     NonEmptyList.one(
                       Action(
                         ActionType.Undefined,
                         Stream
                           .emits(
                             List(
                               Result.Partial(PartialValDouble(0.5)),
                               Result.OK(RetValDouble(1.0))
                             )
                           )
                           .covary[IO],
                         Action.State(Action.ActionState.Idle, Nil)
                       )
                     )
                   )
                 )
               )
             )
           )
          )
        )
      )

    val qss = runToCompletionAllIO(qs0)

    qss.map { x =>
      val a = x.drop(1).headOption.flatMap(_.sequences.get(seqId)) match {
        case Some(Sequence.State.Zipper(zipper, status, _)) =>
          (zipper.focus.focus.execution.headOption match {
            case Some(
                  Action(_, _, Action.State(Action.ActionState.Started, v :: _), _)
                ) =>
              v == PartialValDouble(0.5)
            case _ => false
          }) && status.isRunning
        case _                                              => false
      }
      val b = x.lastOption.flatMap(_.sequences.get(seqId)) match {
        case Some(Sequence.State.Final(seq, status)) =>
          seq.steps.headOption
            .flatMap(_.executions.headOption.map(_.head))
            .map(_.state.runState) == Some(Action.ActionState.Completed(RetValDouble(1.0)))
          && status === SequenceState.Completed
        case _                                       => false
      }
      a && b
    }.assert

  }

  test("uncurrentify should be None when not all executions are completed") {
    assert(stepz0.uncurrentify.isEmpty)
    assert(stepza0.uncurrentify.isEmpty)
    assert(stepza1.uncurrentify.isEmpty)
    assert(stepzr0.uncurrentify.isEmpty)
    assert(stepzr1.uncurrentify.nonEmpty)
    assert(stepzr2.uncurrentify.nonEmpty)
    assert(stepzar0.uncurrentify.isEmpty)
    assert(stepzar1.uncurrentify.isEmpty)
  }

  test("next should be None when there are no more pending executions") {
    assert(stepz0.next.isEmpty)
    assert(stepza0.next.isEmpty)
    assert(stepza1.next.nonEmpty)
    assert(stepzr0.next.isEmpty)
    assert(stepzr1.next.isEmpty)
    assert(stepzr2.next.isEmpty)
    assert(stepzar0.next.isEmpty)
    assert(stepzar1.next.nonEmpty)
  }

  val step0: EngineStep[IO] = EngineStep.init(stepId(1), Nil)
  val step1: EngineStep[IO] = EngineStep.init(stepId(1), List(NonEmptyList.one(action)))
  val step2: EngineStep[IO] =
    EngineStep.init(stepId(2), List(NonEmptyList.of(action, action), NonEmptyList.one(action)))

  test("currentify should be None only when a EngineStep is empty of executions") {
    assert(EngineStep.Zipper.currentify(EngineStep.init(stepId(1), Nil)).isEmpty)
    assert(EngineStep.Zipper.currentify(step0).isEmpty)
    assert(EngineStep.Zipper.currentify(step1).nonEmpty)
    assert(EngineStep.Zipper.currentify(step2).nonEmpty)
  }

  test("status should be completed when it doesn't have any executions") {
    assertEquals(stepz0.toStep.status, StepState.Completed)
  }

  test("status should be Error when at least one Action failed") {
    assert(
      EngineStep
        .Zipper(
          id = stepId(1),
          breakpoint = Breakpoint.Disabled,
          pending = Nil,
          focus = Execution(List(action, actionFailed, actionCompleted)),
          done = Nil,
          rolledback = (Execution(List(action, action, action)), Nil)
        )
        .toStep
        .status === StepState.Failed("Dummy error")
    )
  }

  test("status should be Completed when all actions succeeded") {
    assert(
      EngineStep
        .Zipper(
          id = stepId(1),
          breakpoint = Breakpoint.Disabled,
          pending = Nil,
          focus = Execution(List(actionCompleted, actionCompleted, actionCompleted)),
          done = Nil,
          rolledback = (Execution(List(action, action, action)), Nil)
        )
        .toStep
        .status === StepState.Completed
    )
  }

  test("status should be Running when there are both actions and results") {
    assert(
      EngineStep
        .Zipper(
          id = stepId(1),
          breakpoint = Breakpoint.Disabled,
          pending = Nil,
          focus = Execution(List(actionCompleted, action, actionCompleted)),
          done = Nil,
          rolledback = (Execution(List(action, action, action)), Nil)
        )
        .toStep
        .status === StepState.Running
    )
  }

  test("status should be Pending when there are only pending actions") {
    assert(
      EngineStep
        .Zipper(
          id = stepId(1),
          breakpoint = Breakpoint.Disabled,
          pending = Nil,
          focus = Execution(List(action, action, action)),
          done = Nil,
          rolledback = (Execution(List(action, action, action)), Nil)
        )
        .toStep
        .status === StepState.Pending
    )
  }

}
