// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Id
import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits.*
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import observe.engine.*
import observe.model.*
import observe.model.enums.Resource.TCS
import observe.model.enums.*
import observe.server.TestCommon.*

import java.util.UUID

class StepsViewSuite extends TestCommon {
  val clientId = ClientId(UUID.randomUUID())

  test("StepsView configStatus should build empty without tasks") {
    assert(StepsView.configStatus(Nil).isEmpty)
  }

  test("StepsView configStatus should be all running if none has a result") {
    val status                                = List(Resource.TCS -> ActionStatus.Running)
    val executions: List[ParallelActions[Id]] = List(NonEmptyList.one(running(Resource.TCS)))
    assertEquals(StepsView.configStatus(executions), status)
  }

  test("StepsView configStatus should be all running if none has a result 2") {
    val status                                =
      List(Resource.TCS -> ActionStatus.Running, Instrument.GmosN -> ActionStatus.Running)
    val executions: List[ParallelActions[Id]] =
      List(NonEmptyList.of(running(Resource.TCS), running(Instrument.GmosN)))
    assertEquals(StepsView.configStatus(executions), status)
  }

  test(
    "StepsView configStatus should be some complete and some running if none has a result even when the previous execution is complete"
  ) {
    val status                                =
      List(Resource.TCS -> ActionStatus.Completed, Instrument.GmosN -> ActionStatus.Running)
    val executions: List[ParallelActions[Id]] =
      List(NonEmptyList.one(done(Resource.TCS)),
           NonEmptyList.of(done(Resource.TCS), running(Instrument.GmosN))
      )
    assertEquals(StepsView.configStatus(executions), status)
  }

  test(
    "StepsView configStatus should be some complete and some pending if one will be done in the future"
  ) {
    val status                                =
      List(Resource.TCS -> ActionStatus.Completed, Instrument.GmosN -> ActionStatus.Running)
    val executions: List[ParallelActions[Id]] = List(
      NonEmptyList.one(running(Instrument.GmosN)),
      NonEmptyList.of(done(Resource.TCS), done(Instrument.GmosN))
    )
    assertEquals(StepsView.configStatus(executions), status)
  }

  test("StepsView configStatus should stop at the first with running steps") {
    val executions: List[ParallelActions[Id]] = List(
      NonEmptyList.one(running(Instrument.GmosN)),
      NonEmptyList.of(running(Instrument.GmosN), running(Resource.TCS))
    )
    val status                                =
      List(Resource.TCS -> ActionStatus.Pending, Instrument.GmosN -> ActionStatus.Running)
    assertEquals(StepsView.configStatus(executions), status)
  }

  test(
    "StepsView configStatus should stop evaluating where at least one is running even while some are done"
  ) {
    val executions: List[ParallelActions[Id]] = List(
      NonEmptyList.of(done(Resource.TCS), done(Instrument.GmosN)),
      NonEmptyList.of(done(Resource.TCS), running(Instrument.GmosN)),
      NonEmptyList.of(pendingAction(Resource.TCS),
                      pendingAction(Instrument.GmosN),
                      pendingAction(Resource.Gcal)
      )
    )
    val status                                = List(Resource.TCS -> ActionStatus.Completed,
                      Resource.Gcal    -> ActionStatus.Pending,
                      Instrument.GmosN -> ActionStatus.Running
    )
    assertEquals(StepsView.configStatus(executions), status)
  }

  test("StepsView pending configStatus should build empty without tasks") {
    assert(StepsView.configStatus(Nil).isEmpty)
  }

  test("StepsView pending configStatus should build be all pending while one is running") {
    val status                                = List(Resource.TCS -> ActionStatus.Pending)
    val executions: List[ParallelActions[Id]] = List(NonEmptyList.one(pendingAction(Resource.TCS)))
    assertEquals(StepsView.pendingConfigStatus(executions), status)
  }

  test("StepsView pending configStatus should build be all pending with mixed") {
    val status                                =
      List(Resource.TCS -> ActionStatus.Pending, Instrument.GmosN -> ActionStatus.Pending)
    val executions: List[ParallelActions[Id]] =
      List(NonEmptyList.of(pendingAction(Resource.TCS), done(Instrument.GmosN)))
    assertEquals(StepsView.pendingConfigStatus(executions), status)
  }

  test("StepsView pending configStatus should build be all pending on mixed combinations") {
    val status                                =
      List(Resource.TCS -> ActionStatus.Pending, Instrument.GmosN -> ActionStatus.Pending)
    val executions: List[ParallelActions[Id]] =
      List(NonEmptyList.one(done(Resource.TCS)),
           NonEmptyList.of(done(Resource.TCS), pendingAction(Instrument.GmosN))
      )
    assertEquals(StepsView.pendingConfigStatus(executions), status)
  }

  test("StepsView pending configStatus should build be all pending with multiple resources") {
    val executions: List[ParallelActions[Id]] = List(
      NonEmptyList.of(done(Resource.TCS), pendingAction(Instrument.GmosN)),
      NonEmptyList.of(done(Resource.TCS), pendingAction(Instrument.GmosN)),
      NonEmptyList.of(done(Resource.TCS),
                      pendingAction(Instrument.GmosN),
                      pendingAction(Resource.Gcal)
      )
    )
    val status                                = List(Resource.TCS -> ActionStatus.Pending,
                      Resource.Gcal    -> ActionStatus.Pending,
                      Instrument.GmosN -> ActionStatus.Pending
    )
    assertEquals(StepsView.pendingConfigStatus(executions), status)
  }

  test("StepsView observeStatus should be pending on empty") {
    assertEquals(StepsView.observeStatus(Nil), ActionStatus.Pending)
  }

  test("StepsView observeStatus should be running if there is an action observe") {
    val executions: List[ParallelActions[Id]] = List(NonEmptyList.of(done(Resource.TCS), observing))
    assertEquals(StepsView.observeStatus(executions), ActionStatus.Running)
  }

  test("StepsView observeStatus should be done if there is a result observe") {
    val executions: List[ParallelActions[Id]] = List(NonEmptyList.of(done(Resource.TCS), observed))
    assertEquals(StepsView.observeStatus(executions), ActionStatus.Completed)
  }

  test("StepsView observeStatus should be running if there is a partial result with the file id") {
    val executions: List[ParallelActions[Id]] =
      List(NonEmptyList.of(done(Resource.TCS), fileIdReady))
    assertEquals(StepsView.observeStatus(executions), ActionStatus.Running)
  }

  test("StepsView observeStatus should be paused if there is a paused observe") {
    val executions: List[ParallelActions[Id]] = List(NonEmptyList.of(done(Resource.TCS), paused))
    assertEquals(StepsView.observeStatus(executions), ActionStatus.Paused)
  }

  test("StepsView setOperator should set operator's name") {
    val operator = Operator("Joe")
    val s0       = EngineState.default[IO]
    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setOperator(user, operator), 2)
    } yield sf
      .flatMap(EngineState.operator.get)
      .exists { op =>
        op === operator
      }).assert
  }

  test("StepsView setImageQuality should set Image Quality condition") {
    val iq = ImageQuality.PointTwo
    val s0 = EngineState.default[IO]

    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setImageQuality(iq, user, clientId), 2)
    } yield sf.flatMap(EngineState.conditions.andThen(Conditions.iq).get).exists { op =>
      op === iq
    }).assert
  }

  test("StepsView setWaterVapor should set Water Vapor condition") {
    val wv = WaterVapor.Dry
    val s0 = EngineState.default[IO]
    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setWaterVapor(wv, user, clientId), 2)
    } yield sf.flatMap(EngineState.conditions.andThen(Conditions.wv).get).exists { op =>
      op === wv
    }).assert
  }

  test("StepsView setCloudExtintion should set Cloud Extinction condition") {
    val ce = CloudExtinction.PointThree
    val s0 = EngineState.default[IO]
    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setCloudExtinction(ce, user, clientId), 2)
    } yield sf.flatMap(EngineState.conditions.andThen(Conditions.ce).get).exists { op =>
      op === ce
    }).assert
  }

  test("StepsView setSkyBackground should set Sky Background condition") {
    val sb = SkyBackground.Darkest
    val s0 = EngineState.default[IO]
    for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setSkyBackground(sb, user, clientId), 2)
    } yield sf.flatMap(EngineState.conditions.andThen(Conditions.sb).get).exists { op =>
      op === sb
    }
  }

  // test("StepsView setObserver should set observer's name") {
  //   val observer = Observer("Joe")
  //   val s0       = ODBSequencesLoader
  //     .loadSequenceEndo[IO](none, sequence(seqObsId1), executeEngine)
  //     .apply(EngineState.default[IO])
  //   (for {
  //     oe <- observeEngine
  //     sf <-
  //       advanceN(oe, s0, oe.setObserver(seqObsId1, user, observer), 2)
  //   } yield sf
  //     .flatMap(
  //       EngineState
  //         .sequences[IO]
  //         .andThen(mapIndex[Observation.Id, SequenceData[IO]].index(seqObsId1))
  //         .getOption
  //     )
  //     .flatMap(_.observer)
  //     .exist { case Some(op) =>
  //       op shouldBe observer
  //     }).unsafeRunSync()
  // }

//   "StepsView" should "not run 2nd sequence because it's using the same resource" in {
//     val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
//       seqObsId1,
//       sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.F2, TCS)),
//       executeEngine
//     ) >>>
//       ODBSequencesLoader.loadSequenceEndo[IO](
//         seqObsId2,
//         sequenceWithResources(seqObsId2, Instrument.F2, Set(Instrument.F2)),
//         executeEngine
//       ) >>>
//       EngineState
//         .sequenceStateIndex[IO](seqObsId1)
//         .andThen(Sequence.State.status[IO])
//         .replace(SequenceState.Running.init))(EngineState.default[IO])
//
//     (for {
//       q  <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf <- advanceOne(
//               q,
//               s0,
//               observeEngine.start(q,
//                                   seqObsId2,
//                                   user,
//                                   Observer(""),
//                                   clientId,
//                                   RunOverride.Default
//               )
//             )
//     } yield inside(sf.flatMap(EngineState.sequenceStateIndex(seqObsId2).getOption).map(_.status)) {
//       case Some(status) => assert(status.isIdle)
//     }).unsafeRunSync()
//
//   }
//
//   it should "run 2nd sequence when there are no shared resources" in {
//     val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
//       seqObsId1,
//       sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.F2, TCS)),
//       executeEngine
//     ) >>>
//       ODBSequencesLoader.loadSequenceEndo[IO](
//         seqObsId2,
//         sequenceWithResources(seqObsId2, Instrument.GmosS, Set(Instrument.GmosS)),
//         executeEngine
//       ) >>>
//       EngineState
//         .sequenceStateIndex[IO](seqObsId1)
//         .andThen(Sequence.State.status[IO])
//         .replace(SequenceState.Running.init))(EngineState.default[IO])
//
//     (for {
//       q  <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf <- advanceN(
//               q,
//               s0,
//               observeEngine.start(q,
//                                   seqObsId2,
//                                   user,
//                                   Observer(""),
//                                   clientId,
//                                   RunOverride.Default
//               ),
//               2
//             )
//     } yield inside(sf.flatMap(EngineState.sequenceStateIndex(seqObsId2).getOption).map(_.status)) {
//       case Some(status) => assert(status.isRunning)
//     }).unsafeRunSync()
//   }
//
//   "StepsView configSystem" should "run a system configuration" in {
//     val s0 = ODBSequencesLoader
//       .loadSequenceEndo[IO](
//         seqObsId1,
//         sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.F2, TCS)),
//         executeEngine
//       )
//       .apply(EngineState.default[IO])
//
//     (for {
//       q  <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf <-
//         advanceN(q,
//                  s0,
//                  observeEngine.configSystem(q,
//                                             seqObsId1,
//                                             Observer(""),
//                                             user,
//                                             stepId(1),
//                                             TCS,
//                                             clientId
//                  ),
//                  3
//         )
//     } yield inside(
//       sf.flatMap(
//         EngineState
//           .sequences[IO]
//           .andThen(mapIndex[Observation.Id, SequenceData[IO]].index(seqObsId1))
//           .getOption
//       )
//     ) { case Some(s) =>
//       assertResult(Some(Action.ActionState.Started))(
//         s.seqGen.configActionCoord(stepId(1), TCS).map(s.seq.getSingleState)
//       )
//     }).unsafeRunSync()
//   }
//
//   it should "not run a system configuration if sequence is running" in {
//     val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
//       seqObsId1,
//       sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.F2, TCS)),
//       executeEngine
//     ) >>>
//       EngineState
//         .sequenceStateIndex[IO](seqObsId1)
//         .andThen(Sequence.State.status[IO])
//         .replace(SequenceState.Running.init))(EngineState.default[IO])
//
//     (for {
//       q  <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf <-
//         advanceOne(q,
//                    s0,
//                    observeEngine.configSystem(q,
//                                               seqObsId1,
//                                               Observer(""),
//                                               user,
//                                               stepId(1),
//                                               TCS,
//                                               clientId
//                    )
//         )
//     } yield inside(
//       sf.flatMap(
//         EngineState
//           .sequences[IO]
//           .andThen(mapIndex[Observation.Id, SequenceData[IO]].index(seqObsId1))
//           .getOption
//       )
//     ) { case Some(s) =>
//       assertResult(Some(Action.ActionState.Idle))(
//         s.seqGen.configActionCoord(stepId(1), TCS).map(s.seq.getSingleState)
//       )
//     }).unsafeRunSync()
//   }
//
//   it should "not run a system configuration if system is in use" in {
//     val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
//       seqObsId1,
//       sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.F2, TCS)),
//       executeEngine
//     ) >>>
//       ODBSequencesLoader.loadSequenceEndo[IO](
//         seqObsId2,
//         sequenceWithResources(seqObsId2, Instrument.F2, Set(Instrument.F2)),
//         executeEngine
//       ) >>>
//       EngineState
//         .sequenceStateIndex[IO](seqObsId1)
//         .andThen(Sequence.State.status[IO])
//         .replace(SequenceState.Running.init))(EngineState.default[IO])
//
//     (for {
//       q  <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf <-
//         advanceOne(
//           q,
//           s0,
//           observeEngine.configSystem(q,
//                                      seqObsId2,
//                                      Observer(""),
//                                      user,
//                                      stepId(1),
//                                      Instrument.F2,
//                                      clientId
//           )
//         )
//     } yield inside(
//       sf.flatMap(
//         EngineState
//           .sequences[IO]
//           .andThen(mapIndex[Observation.Id, SequenceData[IO]].index(seqObsId2))
//           .getOption
//       )
//     ) { case Some(s) =>
//       assertResult(Some(Action.ActionState.Idle))(
//         s.seqGen.configActionCoord(stepId(1), Instrument.F2).map(s.seq.getSingleState)
//       )
//     }).unsafeRunSync()
//   }
//
//   it should "run a system configuration when other sequence is running with other systems" in {
//     val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
//       seqObsId1,
//       sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.GmosS, TCS)),
//       executeEngine
//     ) >>>
//       ODBSequencesLoader.loadSequenceEndo[IO](
//         seqObsId2,
//         sequenceWithResources(seqObsId2, Instrument.F2, Set(Instrument.F2)),
//         executeEngine
//       ) >>>
//       EngineState
//         .sequenceStateIndex[IO](seqObsId1)
//         .andThen(Sequence.State.status[IO])
//         .replace(SequenceState.Running.init))(EngineState.default[IO])
//
//     (for {
//       q  <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf <-
//         advanceN(
//           q,
//           s0,
//           observeEngine
//             .configSystem(q,
//                           seqObsId2,
//                           Observer(""),
//                           user,
//                           stepId(1),
//                           Instrument.F2,
//                           clientId
//             ),
//           3
//         )
//     } yield inside(
//       sf.flatMap(
//         EngineState
//           .sequences[IO]
//           .andThen(mapIndex[Observation.Id, SequenceData[IO]].index(seqObsId2))
//           .getOption
//       )
//     ) { case Some(s) =>
//       assertResult(Some(Action.ActionState.Started))(
//         s.seqGen.configActionCoord(stepId(1), Instrument.F2).map(s.seq.getSingleState)
//       )
//     }).unsafeRunSync()
//   }
//
//   "StepsView startFrom" should "start a sequence from an arbitrary step" in {
//     val s0        = ODBSequencesLoader
//       .loadSequenceEndo[IO](seqObsId1, sequenceNSteps(seqObsId1, 5), executeEngine)
//       .apply(EngineState.default[IO])
//     val runStepId = stepId(3)
//
//     (for {
//       q  <- Queue.bounded[IO, executeEngine.EventType](10)
//       _  <- observeEngine.startFrom(q,
//                                     seqObsId1,
//                                     Observer(""),
//                                     runStepId,
//                                     clientId,
//                                     RunOverride.Default
//             )
//       sf <- observeEngine
//               .stream(Stream.fromQueueUnterminated(q))(s0)
//               .map(_._2)
//               .takeThrough(_.sequences.values.exists(_.seq.status.isRunning))
//               .compile
//               .last
//     } yield inside(
//       sf.flatMap(EngineState.sequenceStateIndex(seqObsId1).getOption).map(_.toSequence.steps)
//     ) { case Some(steps) =>
//       assertResult(Some(StepState.Skipped))(steps.get(0).map(_.status))
//       assertResult(Some(StepState.Skipped))(steps.get(1).map(_.status))
//       assertResult(Some(StepState.Completed))(steps.get(2).map(_.status))
//     }).unsafeRunSync()
//   }
//
//   "StepsView startFrom" should "not start the sequence if there is a resource conflict" in {
//     val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
//       seqObsId1,
//       sequenceWithResources(seqObsId1, Instrument.F2, Set(Instrument.F2, TCS)),
//       executeEngine
//     ) >>>
//       ODBSequencesLoader.loadSequenceEndo[IO](
//         seqObsId2,
//         sequenceWithResources(seqObsId2, Instrument.F2, Set(Instrument.F2)),
//         executeEngine
//       ) >>>
//       EngineState
//         .sequenceStateIndex[IO](seqObsId1)
//         .andThen(Sequence.State.status[IO])
//         .replace(SequenceState.Running.init))(EngineState.default[IO])
//
//     val runStepId = stepId(2)
//
//     (for {
//       q  <- Queue.bounded[IO, executeEngine.EventType](10)
//       _  <- observeEngine.startFrom(q,
//                                     seqObsId2,
//                                     Observer(""),
//                                     runStepId,
//                                     clientId,
//                                     RunOverride.Default
//             )
//       sf <- observeEngine
//               .stream(Stream.fromQueueUnterminated(q))(s0)
//               .map(_._2)
//               .takeThrough(_.sequences.get(seqObsId2).exists(_.seq.status.isRunning))
//               .compile
//               .last
//     } yield inside(sf.flatMap(EngineState.sequenceStateIndex(seqObsId2).getOption).map(_.status)) {
//       case Some(status) => assert(status.isIdle)
//     }).unsafeRunSync()
//   }
//
}
