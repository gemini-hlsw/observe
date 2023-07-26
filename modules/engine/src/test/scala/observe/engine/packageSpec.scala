// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all.*
import cats.effect.unsafe.implicits.global
import fs2.Stream
import observe.model.Observation
import org.scalatest.Inside.inside
import org.scalatest.NonImplicitAssertions
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import observe.engine.Sequence.State.Final
import observe.model.{ClientId, SequenceState, StepState}
import observe.model.enums.Instrument.GmosS
import observe.model.enums.Resource.TCS
import observe.model.{ActionType, UserDetails}
import observe.engine.TestUtil.TestState

import scala.concurrent.duration.*
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.std.Semaphore
import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.model.sequence.Atom
import observe.common.test.{observationId, stepId}

class packageSpec extends AnyFlatSpec with NonImplicitAssertions {

  private implicit def logger: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("observe-engine")

  private val atomId = Atom.Id(UUID.fromString("ad387bf4-093d-11ee-be56-0242ac120002"))

  object DummyResult extends Result.RetVal

  /**
   * Emulates TCS configuration in the real world.
   */
  val configureTcs: Action[IO] = fromF[IO](ActionType.Configure(TCS),
                                           for {
                                             _ <- IO(Thread.sleep(200))
                                           } yield Result.OK(DummyResult)
  )

  /**
   * Emulates Instrument configuration in the real world.
   */
  val configureInst: Action[IO] = fromF[IO](ActionType.Configure(GmosS),
                                            for {
                                              _ <- IO(Thread.sleep(200))
                                            } yield Result.OK(DummyResult)
  )

  /**
   * Emulates an observation in the real world.
   */
  val observe: Action[IO] = fromF[IO](ActionType.Observe,
                                      for {
                                        _ <- IO(Thread.sleep(200))
                                      } yield Result.OK(DummyResult)
  )

  val faulty: Action[IO] = fromF[IO](ActionType.Undefined,
                                     for {
                                       _ <- IO(Thread.sleep(100))
                                     } yield Result.Error("There was an error in this action")
  )

  private val clientId: ClientId = ClientId(UUID.randomUUID)

  val executions: List[ParallelActions[IO]] =
    List(NonEmptyList.of(configureTcs, configureInst), NonEmptyList.one(observe))

  val seqId: Observation.Id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(1))
  val qs1: TestState        =
    TestState(
      sequences = Map(
        (seqId,
         Sequence.State.init(
           Sequence.sequence(
             id = observationId(2),
             atomId = atomId,
             steps = List(
               Step.init(
                 id = stepId(1),
                 executions = List(
                   NonEmptyList.of(configureTcs, configureInst), // Execution
                   NonEmptyList.one(observe)                     // Execution
                 )
               ),
               Step.init(
                 id = stepId(2),
                 executions = executions
               )
             )
           )
         )
        )
      )
    )

  private val executionEngine = new Engine[IO, TestState, Unit](TestState)
  private val user            = UserDetails("telops", "Telops")

  def isFinished(status: SequenceState): Boolean = status match {
    case SequenceState.Idle      => true
    case SequenceState.Completed => true
    case SequenceState.Failed(_) => true
    case _                       => false
  }

  def runToCompletion(s0: TestState): Option[TestState] =
    executionEngine
      .process(PartialFunction.empty)(
        Stream.eval(
          IO.pure(Event.start[IO, TestState, Unit](seqId, user, clientId))
        )
      )(s0)
      .drop(1)
      .takeThrough(a => !isFinished(a._2.sequences(seqId).status))
      .compile
      .last
      .unsafeRunSync()
      .map(_._2)

  it should "be in Running status after starting" in {
    val p  = Stream.eval(IO.pure(Event.start[IO, TestState, Unit](seqId, user, clientId)))
    val qs = executionEngine
      .process(PartialFunction.empty)(p)(qs1)
      .take(1)
      .compile
      .last
      .unsafeRunSync()
      .map(_._2)
    assert(qs.exists(s => Sequence.State.isRunning(s.sequences(seqId))))
  }

  it should "be 0 pending executions after execution" in {
    val qs = runToCompletion(qs1)
    assert(qs.exists(_.sequences(seqId).pending.isEmpty))
  }

  it should "be 2 Steps done after execution" in {
    val qs = runToCompletion(qs1)
    assert(qs.exists(_.sequences(seqId).done.length === 2))
  }

  private def actionPause: Option[TestState] = {
    val s0: TestState = TestState(
      Map(
        seqId -> Sequence.State.init(
          Sequence.sequence(
            id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(1)),
            atomId = atomId,
            steps = List(
              Step.init(
                id = stepId(1),
                executions = List(
                  NonEmptyList.one(
                    fromF[IO](ActionType.Undefined, IO(Result.Paused(new Result.PauseContext {})))
                  )
                )
              )
            )
          )
        )
      )
    )
    val p             = Stream.eval(IO.pure(Event.start[IO, TestState, Unit](seqId, user, clientId)))

    // take(3): Start, Executing, Paused
    executionEngine
      .process(PartialFunction.empty)(p)(s0)
      .take(3)
      .compile
      .last
      .unsafeRunSync()
      .map(_._2)
  }

  "sequence state" should "stay as running when action pauses itself" in {
    assert(actionPause.exists(s => Sequence.State.isRunning(s.sequences(seqId))))
  }

  "engine" should "change action state to Paused if output is Paused" in {
    val r = actionPause
    assert(r.exists(_.sequences(seqId).current.execution.forall(Action.paused)))
  }

  "engine" should "run sequence to completion after resuming a paused action" in {
    val p: Stream[IO, executionEngine.EventType] =
      Stream.eval(
        IO.pure(
          Event.actionResume[IO, TestState, Unit](seqId, 0, Stream.eval(IO(Result.OK(DummyResult))))
        )
      )

    val result = actionPause.flatMap(
      executionEngine
        .process(PartialFunction.empty)(p)(_)
        .drop(1)
        .takeThrough(a => !isFinished(a._2.sequences(seqId).status))
        .compile
        .last
        .unsafeRunTimed(5.seconds)
    )
    val qso    = result.flatMap(_.map(_._2))

    assert(
      qso.forall(x =>
        x.sequences(seqId).current.actions.isEmpty && (x
          .sequences(seqId)
          .status === SequenceState.Completed)
      )
    )

  }

  "engine" should "keep processing input messages regardless of how long ParallelActions take" in {
    val result = (for {
      q           <- Stream.eval(cats.effect.std.Queue.bounded[IO, executionEngine.EventType](1))
      startedFlag <- Stream.eval(Semaphore.apply[IO](0))
      finishFlag  <- Stream.eval(Semaphore.apply[IO](0))
      r           <- {
        val qs = TestState(
          Map(
            seqId -> Sequence.State.init(
              Sequence.sequence(
                id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(2)),
                atomId = atomId,
                steps = List(
                  Step.init(
                    id = stepId(1),
                    executions = List(
                      NonEmptyList.one(
                        fromF[IO](ActionType.Configure(TCS),
                                  startedFlag.release *> finishFlag.acquire *> IO.pure(
                                    Result.OK(DummyResult)
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
        Stream.eval(
          List(
            List[IO[Unit]](
              q.offer(Event.start[IO, TestState, Unit](seqId, user, clientId)),
              startedFlag.acquire,
              q.offer(Event.nullEvent),
              q.offer(Event.getState[IO, TestState, Unit] { _ =>
                Stream.eval(finishFlag.release).as(Event.nullEvent[IO, TestState, Unit]).some
              })
            ).sequence,
            executionEngine
              .process(PartialFunction.empty)(Stream.fromQueueUnterminated(q))(qs)
              .drop(1)
              .takeThrough(a => !isFinished(a._2.sequences(seqId).status))
              .compile
              .drain
          ).parSequence
        )
      }
    } yield r).compile.last.unsafeRunTimed(5.seconds).flatten

    assert(result.isDefined)
  }

  "engine" should "not capture runtime exceptions." in {
    def s0(e: Throwable): TestState = TestState(
      Map(
        (seqId,
         Sequence.State.init(
           Sequence.sequence(
             id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(4)),
             atomId = atomId,
             steps = List(
               Step.init(
                 id = stepId(1),
                 executions = List(
                   NonEmptyList.one(
                     fromF[IO](ActionType.Undefined,
                               IO.apply {
                                 throw e
                               }
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

    assertThrows[java.lang.RuntimeException](
      runToCompletion(s0(new java.lang.RuntimeException))
    )
  }

  it should "skip steps marked to be skipped at the beginning of the sequence." in {
    val s0: TestState = TestState(
      Map(
        (seqId,
         Sequence.State.init(
           Sequence.sequence(
             id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(6)),
             atomId = atomId,
             steps = List(
               Step
                 .init(id = stepId(1), executions = executions)
                 .copy(skipMark = Step.SkipMark(true)),
               Step.init(id = stepId(2), executions = executions),
               Step.init(id = stepId(3), executions = executions)
             )
           )
         )
        )
      )
    )

    val sf = runToCompletion(s0)

    inside(sf.map(_.sequences(seqId).done.map(_.status))) { case Some(stepSs) =>
      assert(stepSs === List(StepState.Skipped, StepState.Completed, StepState.Completed))
    }
  }

  it should "skip steps marked to be skipped in the middle of the sequence." in {
    val s0: TestState = TestState(
      Map(
        (seqId,
         Sequence.State.init(
           Sequence.sequence(
             id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(7)),
             atomId = atomId,
             steps = List(
               Step.init(id = stepId(1), executions = executions),
               Step
                 .init(id = stepId(2), executions = executions)
                 .copy(skipMark = Step.SkipMark(true)),
               Step.init(id = stepId(3), executions = executions)
             )
           )
         )
        )
      )
    )

    val sf = runToCompletion(s0)

    inside(sf.map(_.sequences(seqId).done.map(_.status))) { case Some(stepSs) =>
      assert(stepSs === List(StepState.Completed, StepState.Skipped, StepState.Completed))
    }
  }

  it should "skip several steps marked to be skipped." in {
    val s0: TestState = TestState(
      Map(
        (seqId,
         Sequence.State.init(
           Sequence.sequence(
             id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(8)),
             atomId = atomId,
             steps = List(
               Step.init(id = stepId(1), executions = executions),
               Step
                 .init(id = stepId(2), executions = executions)
                 .copy(skipMark = Step.SkipMark(true)),
               Step
                 .init(id = stepId(3), executions = executions)
                 .copy(skipMark = Step.SkipMark(true)),
               Step
                 .init(id = stepId(4), executions = executions)
                 .copy(skipMark = Step.SkipMark(true)),
               Step.init(id = stepId(5), executions = executions)
             )
           )
         )
        )
      )
    )

    val sf = runToCompletion(s0)

    inside(sf.map(_.sequences(seqId).done.map(_.status))) { case Some(stepSs) =>
      assert(
        stepSs === List(StepState.Completed,
                        StepState.Skipped,
                        StepState.Skipped,
                        StepState.Skipped,
                        StepState.Completed
        )
      )
    }
  }

  it should "skip steps marked to be skipped at the end of the sequence." in {
    val s0: TestState = TestState(
      Map(
        (seqId,
         Sequence.State.init(
           Sequence.sequence(
             id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(1)),
             atomId = atomId,
             steps = List(
               Step.init(id = stepId(1), executions = executions),
               Step.init(id = stepId(2), executions = executions),
               Step
                 .init(id = stepId(3), executions = executions)
                 .copy(skipMark = Step.SkipMark(true))
             )
           )
         )
        )
      )
    )

    val sf = runToCompletion(s0)

    inside(sf.map(_.sequences(seqId))) { case Some(s @ Final(_, SequenceState.Completed)) =>
      assert(
        s.done.map(_.status) === List(StepState.Completed, StepState.Completed, StepState.Skipped)
      )
    }
  }

  it should "skip a step marked to be skipped even if it is the only one." in {
    val s0: TestState = TestState(
      Map(
        (seqId,
         Sequence.State.init(
           Sequence.sequence(
             id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(1)),
             atomId = atomId,
             steps = List(
               Step
                 .init(id = stepId(1), executions = executions)
                 .copy(skipMark = Step.SkipMark(true))
             )
           )
         )
        )
      )
    )

    val sf = runToCompletion(s0)

    inside(sf.map(_.sequences(seqId).done.map(_.status))) { case Some(stepSs) =>
      assert(stepSs === List(StepState.Skipped))
    }
  }

  it should "skip steps marked to be skipped at the beginning of the sequence, even if they have breakpoints." in {
    val s0: TestState = TestState(
      Map(
        (seqId,
         Sequence.State.init(
           Sequence.sequence(
             id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(1)),
             atomId = atomId,
             steps = List(
               Step
                 .init(id = stepId(1), executions = executions)
                 .copy(skipMark = Step.SkipMark(true)),
               Step
                 .init(id = stepId(2), executions = executions)
                 .copy(skipMark = Step.SkipMark(true), breakpoint = Step.BreakpointMark(true)),
               Step.init(id = stepId(3), executions = executions)
             )
           )
         )
        )
      )
    )

    val sf = runToCompletion(s0)

    inside(sf.map(_.sequences(seqId).done.map(_.status))) { case Some(stepSs) =>
      assert(stepSs === List(StepState.Skipped, StepState.Skipped, StepState.Completed))
    }
  }

  it should "skip the leading steps if marked to be skipped, even if they have breakpoints and are the last ones." in {
    val s0: TestState = TestState(
      Map(
        (seqId,
         Sequence.State.init(
           Sequence.sequence(
             lucuma.core.model.Observation.Id(PosLong.unsafeFrom(1)),
             atomId = atomId,
             steps = List(
               Step
                 .init(id = stepId(1), executions = executions)
                 .copy(skipped = Step.Skipped(true)),
               Step
                 .init(id = stepId(2), executions = executions)
                 .copy(skipMark = Step.SkipMark(true), breakpoint = Step.BreakpointMark(true)),
               Step
                 .init(id = stepId(3), executions = executions)
                 .copy(skipMark = Step.SkipMark(true), breakpoint = Step.BreakpointMark(true))
             )
           )
         )
        )
      )
    )

    val sf = runToCompletion(s0)

    inside(sf.map(_.sequences(seqId))) { case Some(s @ Final(_, SequenceState.Completed)) =>
      assert(s.done.map(_.status) === List(StepState.Skipped, StepState.Skipped, StepState.Skipped))
    }
  }

  it should "skip steps marked to be skipped in the middle of the sequence, but honoring breakpoints." in {
    val s0: TestState = TestState(
      Map(
        (seqId,
         Sequence.State.init(
           Sequence.sequence(
             id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(1)),
             atomId = atomId,
             steps = List(
               Step.init(id = stepId(1), executions = executions),
               Step
                 .init(id = stepId(2), executions = executions)
                 .copy(skipMark = Step.SkipMark(true)),
               Step
                 .init(id = stepId(3), executions = executions)
                 .copy(skipMark = Step.SkipMark(true), breakpoint = Step.BreakpointMark(true)),
               Step.init(id = stepId(4), executions = executions)
             )
           )
         )
        )
      )
    )

    val sf = runToCompletion(s0)

    inside(sf.map(_.sequences(seqId).done.map(_.status))) { case Some(stepSs) =>
      assert(stepSs === List(StepState.Completed, StepState.Skipped))
    }
  }

  it should "run single Action" in {
    val dummy         = new AtomicInteger(0)
    val markVal       = 1
    val sId           = stepId(1)
    val s0: TestState = TestState(
      Map(
        (seqId,
         Sequence.State.init(
           Sequence.sequence(
             id = seqId,
             atomId = atomId,
             steps = List(
               Step.init(id = sId,
                         executions = List(
                           NonEmptyList.one(
                             fromF[IO](ActionType.Undefined,
                                       IO {
                                         dummy.set(markVal)
                                         Result.OK(DummyResult)
                                       }
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

    val c     = ActionCoordsInSeq(sId, ExecutionIndex(0), ActionIndex(0))
    val event = Event.modifyState[IO, TestState, Unit](
      executionEngine.startSingle(ActionCoords(seqId, c)).void
    )
    val sfs   = executionEngine
      .process(PartialFunction.empty)(Stream.eval(IO.pure(event)))(s0)
      .map(_._2)
      .take(2)
      .compile
      .toList
      .unsafeRunSync()

    /**
     * First state update must have the action started. Second state update must have the action
     * finished. The value in `dummy` must change. That is prove that the `Action` run.
     */
    inside(sfs) { case a :: b :: _ =>
      assert(TestState.sequenceStateIndex(seqId).getOption(a).exists(_.getSingleState(c).started))
      assert(TestState.sequenceStateIndex(seqId).getOption(b).exists(_.getSingleState(c).completed))
      assert(dummy.get === markVal)
    }
  }

  val qs2: TestState =
    TestState(
      sequences = Map(
        (seqId,
         Sequence.State.init(
           Sequence.sequence(
             id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(1)),
             atomId = atomId,
             steps = List(
               Step.init(
                 id = stepId(1),
                 executions = List(
                   NonEmptyList.one(
                     Action[IO](ActionType.Undefined,
                                Stream(Result.OK(DummyResult)).covary[IO],
                                Action.State(Action.ActionState.Completed(DummyResult), List.empty)
                     )
                   )
                 )
               ),
               Step.init(
                 id = stepId(2),
                 executions = executions
               ),
               Step.init(
                 id = stepId(3),
                 executions = executions
               ),
               Step.init(
                 id = stepId(4),
                 executions = executions
               )
             )
           )
         )
        )
      )
    )

  it should "be able to start sequence from arbitrary step" in {
    val event = Event.modifyState[IO, TestState, Unit](
      executionEngine
        .startFrom(seqId, stepId(3))
        .void
    )

    val sf = executionEngine
      .process(PartialFunction.empty)(Stream.eval(IO.pure(event)))(qs2)
      .drop(1)
      .takeThrough(a => !isFinished(a._2.sequences(seqId).status))
      .compile
      .last
      .unsafeRunSync()
      .map(_._2)

    inside(sf.flatMap(_.sequences.get(seqId).map(_.toSequence))) { case Some(seq) =>
      assertResult(Some(StepState.Completed))(seq.steps.get(0).map(_.status))
      assertResult(Some(StepState.Skipped))(seq.steps.get(1).map(_.status))
      assertResult(Some(StepState.Completed))(seq.steps.get(2).map(_.status))
      assertResult(Some(StepState.Completed))(seq.steps.get(3).map(_.status))
    }

  }

}
