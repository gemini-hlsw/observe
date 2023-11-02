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
import observe.common.test.*
import observe.engine.Action
import observe.engine.Response
import observe.engine.Response.Observed
import observe.engine.Result
import observe.engine.Sequence
import observe.model.ActionType
import observe.model.Conditions
import observe.model.SequenceState
import observe.model.dhs.*
import observe.server.SequenceGen.StepStatusGen
import observe.server.TestCommon.*

import java.time.temporal.ChronoUnit

class SeqTranslateSuite extends TestCommon {

  private val fileId = "DummyFileId"

  private def observeActions(state: Action.ActionState): NonEmptyList[Action[IO]] =
    NonEmptyList.one(
      Action(ActionType.Observe,
             Stream.emit(Result.OK(Observed(toImageFileId(fileId)))).covary[IO],
             Action.State(state, Nil)
      )
    )

  private val seqg = sequence(seqObsId1).copy(
    steps = List(
      SequenceGen.PendingStepGen(
        stepId(1),
        Monoid.empty[DataId],
        Set(Instrument.GmosNorth),
        _ => InstrumentSystem.Uncontrollable,
        SequenceGen.StepActionsGen(Map.empty,
                                   (_, _) => List(observeActions(Action.ActionState.Idle))
        ),
        StepStatusGen.Null,
        dynamicCfg1,
        stepCfg1,
        breakpoint = Breakpoint.Disabled
      )
    )
  )

  private val baseState: EngineState[IO] =
    (ODBSequencesLoader
      .loadSequenceEndo[IO](None, seqg, EngineState.instrumentLoaded(Instrument.GmosNorth)) >>>
      EngineState
        .sequenceStateIndex[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.init))(EngineState.default[IO])

  // Observe started
  private val s0: EngineState[IO] = EngineState
    .sequenceStateIndex[IO](seqObsId1)
    .modify(_.start(0))(baseState)
  // Observe pending
  private val s1: EngineState[IO] = baseState
  // Observe completed
  private val s2: EngineState[IO] = EngineState
    .sequenceStateIndex[IO](seqObsId1)
    .modify(_.mark(0)(Result.OK(Observed(toImageFileId(fileId)))))(baseState)
  // Observe started, but with file Id already allocated
  private val s3: EngineState[IO] = EngineState
    .sequenceStateIndex[IO](seqObsId1)
    .modify(
      _.start(0).mark(0)(Result.Partial(FileIdAllocated(toImageFileId(fileId))))
    )(baseState)
  // Observe paused
  private val s4: EngineState[IO] = EngineState
    .sequenceStateIndex[IO](seqObsId1)
    .modify(
      _.mark(0)(
        Result.Paused(
          ObserveContext[IO](
            _ => Stream.emit(Result.OK(Observed(toImageFileId(fileId)))).covary[IO],
            _ => Stream.empty,
            Stream.emit(Result.OK(Observed(toImageFileId(fileId)))).covary[IO],
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
    .modify(_.mark(0)(Result.OKAborted(Response.Aborted(toImageFileId(fileId)))))(baseState)

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
