// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.data.NonEmptyList
import cats.data.OptionT
import cats.effect.IO
import cats.effect.std.Semaphore
import cats.syntax.all.*
import eu.timepit.refined.types.numeric.PosLong
import fs2.Stream
import lucuma.core.enums.Instrument.GmosSouth
import lucuma.core.model.OrcidId
import lucuma.core.model.OrcidProfile
import lucuma.core.model.StandardRole
import lucuma.core.model.StandardUser
import lucuma.core.model.User
import lucuma.core.model.sequence.Atom
import lucuma.refined.*
import observe.common.test.observationId
import observe.common.test.stepId
import observe.engine.TestUtil.TestState
import observe.model.ActionType
import observe.model.ClientId
import observe.model.Observation
import observe.model.SequenceState
import observe.model.enums.Resource
import observe.model.enums.Resource.TCS
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.*

def user =
  StandardUser(
    User.Id(1.refined),
    StandardRole.Staff(StandardRole.Id(1.refined)),
    Nil,
    OrcidProfile(
      OrcidId.fromValue("0000-0001-5558-6297").getOrElse(sys.error("OrcidId")),
      Some("John"),
      Some("Doe"),
      None,
      None
    )
  )

class PackageSuite extends munit.CatsEffectSuite {

  private given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("observe-engine")

  private val atomId = Atom.Id(UUID.fromString("ad387bf4-093d-11ee-be56-0242ac120002"))

  object DummyResult extends Result.RetVal

  /**
   * Emulates TCS configuration in the real world.
   */
  val configureTcs: Action[IO] = fromF[IO](ActionType.Configure(TCS),
                                           for {
                                             _ <- IO.sleep(200.milliseconds)
                                           } yield Result.OK(DummyResult)
  )

  /**
   * Emulates Instrument configuration in the real world.
   */
  val configureInst: Action[IO] = fromF[IO](ActionType.Configure(GmosSouth),
                                            for {
                                              _ <- IO.sleep(200.milliseconds)
                                            } yield Result.OK(DummyResult)
  )

  /**
   * Emulates an observation in the real world.
   */
  val observe: Action[IO] = fromF[IO](ActionType.Observe,
                                      for {
                                        _ <- IO.sleep(200.milliseconds)
                                      } yield Result.OK(DummyResult)
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
               EngineStep.init(
                 id = stepId(1),
                 executions = List(
                   NonEmptyList.of(configureTcs, configureInst), // Execution
                   NonEmptyList.one(observe)                     // Execution
                 )
               ),
               EngineStep.init(
                 id = stepId(2),
                 executions = executions
               )
             )
           )
         )
        )
      )
    )

  private def executionEngine = Engine.build[IO, TestState, Unit](
    TestState,
    (eng, obsId) => eng.startNewAtom(obsId)
  )

  def isFinished(status: SequenceState): Boolean = status match {
    case SequenceState.Idle      => true
    case SequenceState.Completed => true
    case SequenceState.Failed(_) => true
    case _                       => false
  }

  def runToCompletion(s0: TestState): IO[Option[TestState]] =
    for {
      eng <- executionEngine
      _   <- eng.offer(Event.start[IO, TestState, Unit](seqId, user, clientId))
      v   <- eng
               .process(PartialFunction.empty)(s0)
               .drop(1)
               .takeThrough(a => !isFinished(a._2.sequences(seqId).status))
               .compile
               .last
    } yield v.map(_._2)

  test("it should be in Running status after starting") {
    val qs =
      for {
        eng <- executionEngine
        _   <- eng.offer(Event.start[IO, TestState, Unit](seqId, user, clientId))
        v   <- eng
                 .process(PartialFunction.empty)(qs1)
                 .take(1)
                 .compile
                 .last
      } yield v.map(_._2)

    qs.map(_.exists(s => Sequence.State.isRunning(s.sequences(seqId)))).assert
  }

  test("there should be 0 pending executions after execution") {
    val qs = runToCompletion(qs1)
    qs.map(_.exists(_.sequences(seqId).pending.isEmpty)).assert
  }

  test("there should be 2 Steps done after execution") {
    val qs = runToCompletion(qs1)
    qs.map(_.exists(_.sequences(seqId).done.length === 2)).assert
  }

  private def actionPause: IO[Option[TestState]] = {
    val s0: TestState = TestState(
      Map(
        seqId -> Sequence.State.init(
          Sequence.sequence(
            id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(1)),
            atomId = atomId,
            steps = List(
              EngineStep.init(
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

    // take(3): Start, Executing, Paused
    for {
      eng <- executionEngine
      _   <- eng.offer(Event.start[IO, TestState, Unit](seqId, user, clientId))
      v   <- eng
               .process(PartialFunction.empty)(s0)
               .take(3)
               .compile
               .last
    } yield v.map(_._2)

  }

  test("sequencestate should stay as running when action pauses itself") {
    actionPause.map(_.exists(s => Sequence.State.isRunning(s.sequences(seqId)))).assert
  }

  test("engine should change action state to Paused if output is Paused") {
    val r = actionPause
    r.map(_.exists(_.sequences(seqId).current.execution.forall(Action.paused))).assert
  }

  test("run sequence to completion after resuming a paused action") {
    val result =
      for {
        s   <- OptionT(actionPause)
        eng <- OptionT.liftF(executionEngine)
        _   <- OptionT.liftF(
                 eng.offer(
                   Event.actionResume[IO, TestState, Unit](seqId,
                                                           0,
                                                           Stream.eval(IO(Result.OK(DummyResult)))
                   )
                 )
               )
        r   <- OptionT.liftF(
                 eng
                   .process(PartialFunction.empty)(s)
                   .drop(1)
                   .takeThrough(a => !isFinished(a._2.sequences(seqId).status))
                   .compile
                   .last
               )
        m   <- OptionT.fromOption(r.map(_._2))
      } yield m

    result.value
      .map(
        _.forall(x =>
          x.sequences(seqId).current.actions.isEmpty && (x
            .sequences(seqId)
            .status === SequenceState.Completed)
        )
      )
      .assert

  }

  test(
    "engine should keep processing input messages regardless of how long ParallelActions take"
  ) {
    val result = for {
      startedFlag <- Semaphore.apply[IO](0)
      finishFlag  <- Semaphore.apply[IO](0)
      eng         <- executionEngine
      r           <- {
        val qs = TestState(
          Map(
            seqId -> Sequence.State.init(
              Sequence.sequence(
                id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(2)),
                atomId = atomId,
                steps = List(
                  EngineStep.init(
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
        List(
          List[IO[Unit]](
            eng.offer(Event.start[IO, TestState, Unit](seqId, user, clientId)),
            startedFlag.acquire,
            eng.offer(Event.nullEvent),
            eng.offer(Event.getState[IO, TestState, Unit] { _ =>
              Stream.eval(finishFlag.release).as(Event.nullEvent[IO, TestState, Unit]).some
            })
          ).sequence,
          eng
            .process(PartialFunction.empty)(qs)
            .drop(1)
            .takeThrough(a => !isFinished(a._2.sequences(seqId).status))
            .compile
            .drain
        ).parSequence
      }
    } yield r

    result.map(_.nonEmpty).assert
  }

  test("engine should not capture runtime exceptions.") {
    def s0(e: Throwable): TestState = TestState(
      Map(
        (seqId,
         Sequence.State.init(
           Sequence.sequence(
             id = lucuma.core.model.Observation.Id(PosLong.unsafeFrom(4)),
             atomId = atomId,
             steps = List(
               EngineStep.init(
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

    runToCompletion(s0(new java.lang.RuntimeException)).attempt.map {
      case Left(_: java.lang.RuntimeException) => true
      case _                                   => false
    }.assert
  }

  test("engine should run single Action") {
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
               EngineStep.init(id = sId,
                               executions = List(
                                 NonEmptyList.one(
                                   fromF[IO](ActionType.Configure(Resource.TCS),
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

    val c                                       = ActionCoordsInSeq(sId, ExecutionIndex(0), ActionIndex(0))
    def event(eng: Engine[IO, TestState, Unit]) = Event.modifyState[IO, TestState, Unit](
      eng.startSingle(ActionCoords(seqId, c)).void
    )

    val sfs =
      for {
        eng <- executionEngine
        _   <- eng.offer(event(eng))
        v   <- eng
                 .process(PartialFunction.empty)(s0)
                 .map(_._2)
                 .take(2)
                 .compile
                 .toList
      } yield v

    /**
     * First state update must have the action started. Second state update must have the action
     * finished. The value in `dummy` must change. That is prove that the `Action` run.
     */
    sfs.map {
      case a :: b :: _ =>
        TestState.sequenceStateIndex(seqId).getOption(a).exists(_.getSingleState(c).started) &&
        TestState.sequenceStateIndex(seqId).getOption(b).exists(_.getSingleState(c).completed) &&
        dummy.get === markVal
      case _           =>
        false
    }.assert
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
               EngineStep.init(
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
               EngineStep.init(
                 id = stepId(2),
                 executions = executions
               ),
               EngineStep.init(
                 id = stepId(3),
                 executions = executions
               ),
               EngineStep.init(
                 id = stepId(4),
                 executions = executions
               )
             )
           )
         )
        )
      )
    )

}
