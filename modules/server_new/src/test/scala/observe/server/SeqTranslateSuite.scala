// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Monoid
import cats.data.NonEmptyList
import cats.effect.*
import cats.syntax.all.*
import fs2.Stream
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.enums.Site
import lucuma.core.util.TimeSpan
import monocle.syntax.all.focus
import observe.common.test.*
import observe.engine.Action
import observe.engine.EngineStep
import observe.engine.Execution
import observe.engine.Response
import observe.engine.Response.Observed
import observe.engine.Result
import observe.engine.Sequence
import observe.engine.Sequence.State
import observe.model.ActionType
import observe.model.Conditions
import observe.model.SequenceState
import observe.model.dhs.*
import observe.server.SequenceGen.StepStatusGen
import observe.server.TestCommon.*

import java.time.temporal.ChronoUnit
import scala.annotation.tailrec

class SeqTranslateSuite extends TestCommon {

  private val fileId = "DummyFileId"

  private def observeActions(state: Action.ActionState): NonEmptyList[Action[IO]] =
    NonEmptyList.one(
      Action(ActionType.Observe,
             Stream.emit(Result.OK(Observed(ImageFileId(fileId)))).covary[IO],
             Action.State(state, Nil)
      )
    )

  private val seqg = sequence(seqObsId1)
    .focus(_.nextAtom.steps)
    .replace(
      List(
        SequenceGen.PendingStepGen(
          stepId(1),
          Monoid.empty[DataId],
          Set(Instrument.GmosNorth),
          _ => InstrumentSystem.Uncontrollable,
          SequenceGen.StepActionsGen(
            odbAction[IO],
            odbAction[IO],
            Map.empty,
            odbAction[IO],
            odbAction[IO],
            (_, _) => List(observeActions(Action.ActionState.Idle)),
            odbAction[IO],
            odbAction[IO]
          ),
          StepStatusGen.Null,
          dynamicCfg1,
          stepCfg1,
          breakpoint = Breakpoint.Disabled
        )
      )
    )

  // Function to advance the execution of a step up to certain Execution
  @tailrec
  private def advanceStepUntil[F[_]](
    st:   EngineStep.Zipper[F],
    cond: EngineStep.Zipper[F] => Boolean
  ): EngineStep.Zipper[F] =
    if (cond(st)) st
    else
      st match {
        case EngineStep.Zipper(_, _, _, p :: ps, f, d, _) =>
          advanceStepUntil(
            st.copy(
              pending = ps,
              focus = Execution(p.toList),
              done = NonEmptyList
                .fromList(
                  f.execution.map(a =>
                    a.kind match {
                      case ActionType.Observe        =>
                        a.copy(state =
                          Action.State(
                            Action.ActionState.Completed(Response.Observed(ImageFileId(fileId))),
                            List.empty
                          )
                        )
                      case ActionType.Undefined      =>
                        a.copy(state =
                          Action.State(Action.ActionState.Completed(Response.Ignored), List.empty)
                        )
                      case ActionType.Configure(sys) =>
                        a.copy(state =
                          Action.State(Action.ActionState.Completed(Response.Configured(sys)),
                                       List.empty
                          )
                        )
                      case ActionType.OdbEvent       =>
                        a.copy(state =
                          Action.State(Action.ActionState.Completed(Response.Ignored), List.empty)
                        )
                    }
                  )
                )
                .map(d.appended)
                .getOrElse(d)
            ),
            cond
          )
        case _                                            => st
      }

  val baseState: EngineState[IO] =
    (ODBSequencesLoader
      .loadSequenceEndo[IO](None, seqg, EngineState.instrumentLoaded(Instrument.GmosNorth)) >>>
      EngineState
        .sequenceStateIndex[IO](seqObsId1)
        .modify {
          case State.Zipper(zipper, status, singleRuns) =>
            State.Zipper(zipper.copy(focus =
                           advanceStepUntil(zipper.focus,
                                            _.focus.execution.exists(_.kind === ActionType.Observe)
                           )
                         ),
                         status,
                         singleRuns
            )
          case s @ State.Final(_, _)                    => s
        } >>>
      EngineState
        .sequenceStateIndex[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.Init))(EngineState.default[IO])

  // Observe started
  private val s0: EngineState[IO] = EngineState
    .sequenceStateIndex[IO](seqObsId1)
    .modify(_.start(0))(baseState)
  // Observe pending
  private val s1: EngineState[IO] = baseState
  // Observe completed
  private val s2: EngineState[IO] = EngineState
    .sequenceStateIndex[IO](seqObsId1)
    .modify(_.mark(0)(Result.OK(Observed(ImageFileId(fileId)))))(baseState)
  // Observe started, but with file Id already allocated
  private val s3: EngineState[IO] = EngineState
    .sequenceStateIndex[IO](seqObsId1)
    .modify(
      _.start(0).mark(0)(Result.Partial(FileIdAllocated(ImageFileId(fileId))))
    )(baseState)
  // Observe paused
  private val s4: EngineState[IO] = EngineState
    .sequenceStateIndex[IO](seqObsId1)
    .modify(
      _.mark(0)(
        Result.Paused(
          ObserveContext[IO](
            _ => Stream.emit(Result.OK(Observed(ImageFileId(fileId)))).covary[IO],
            _ => Stream.empty,
            Stream.emit(Result.OK(Observed(ImageFileId(fileId)))).covary[IO],
            Stream.eval(ObserveFailure.Aborted(seqObsId1).raiseError[IO, Result]),
            TimeSpan.unsafeFromDuration(1, ChronoUnit.SECONDS)
          )
        )
      )
    )(baseState)
  // Observe failed
  private val s5: EngineState[IO] = EngineState
    .sequenceStateIndex[IO](seqObsId1)
    .modify(_.mark(0)(Result.Error("error")))(baseState)
  // Observe aborted
  private val s6: EngineState[IO] = EngineState
    .sequenceStateIndex[IO](seqObsId1)
    .modify(_.mark(0)(Result.OKAborted(Response.Aborted(ImageFileId(fileId)))))(baseState)

  private val translator: IO[SeqTranslate[IO]] = for {
    systems <- defaultSystems
    c       <- Ref.of[IO, Conditions](Conditions.Default)
    st      <- SeqTranslate(Site.GS, systems, c)
  } yield st

  test("SeqTranslate trigger stopObserve command only if exposure is in progress") {
    translator.map { t =>
      assert(t.stopObserve(seqObsId1, graceful = false).apply(s0).isDefined)
      assert(t.stopObserve(seqObsId1, graceful = false).apply(s1).isEmpty)
      assert(t.stopObserve(seqObsId1, graceful = false).apply(s2).isEmpty)
      assert(t.stopObserve(seqObsId1, graceful = false).apply(s3).isDefined)
      assert(t.stopObserve(seqObsId1, graceful = false).apply(s4).isDefined)
      assert(t.stopObserve(seqObsId1, graceful = false).apply(s5).isEmpty)
      assert(t.stopObserve(seqObsId1, graceful = false).apply(s6).isEmpty)
    }
  }

  test("SeqTranslate trigger abortObserve command only if exposure is in progress") {
    translator.map { t =>
      assert(t.abortObserve(seqObsId1).apply(s0).isDefined)
      assert(t.abortObserve(seqObsId1).apply(s1).isEmpty)
      assert(t.abortObserve(seqObsId1).apply(s2).isEmpty)
      assert(t.abortObserve(seqObsId1).apply(s3).isDefined)
      assert(t.abortObserve(seqObsId1).apply(s4).isDefined)
      assert(t.abortObserve(seqObsId1).apply(s5).isEmpty)
      assert(t.abortObserve(seqObsId1).apply(s6).isEmpty)
    }
  }

}
