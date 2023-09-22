// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.effect.IO
import cats.syntax.all.*
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.WaterVapor
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.SkyBackground
import observe.model.Operator
import observe.model.UserDetails
import observe.model.Conditions

class ObserveEngineSuite extends TestCommon {

  test("ObserveEngine setOperator should set operator's name") {
    val operator = Operator("Joe")
    val s0       = EngineState.default[IO]
    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setOperator(UserDetails("", ""), operator), 2)
    } yield sf.flatMap(EngineState.operator.get).exists { op =>
      op === operator
    }).assert
  }

  test("ObserveEngine setImageQuality should set Image Quality condition") {
    val iq = ImageQuality.PointEight
    val s0 = EngineState.default[IO]

    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setImageQuality(iq, UserDetails("", "")), 2)
    } yield sf.flatMap(EngineState.conditions.andThen(Conditions.iq).get).exists { op =>
      op === iq
    }).assert

  }

  test("ObserveEngine setWaterVapor should set Water Vapor condition") {
    val wv = WaterVapor.VeryDry
    val s0 = EngineState.default[IO]
    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setWaterVapor(wv, UserDetails("", "")), 2)
    } yield sf.flatMap(EngineState.conditions.andThen(Conditions.wv).get).exists { op =>
      op === wv
    }).assert
  }

  test("ObserveEngine setCloudExtinction should set Cloud Extinction condition") {
    val cc = CloudExtinction.TwoPointZero
    val s0 = EngineState.default[IO]
    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setCloudExtinction(cc, UserDetails("", "")), 2)
    } yield sf.flatMap(EngineState.conditions.andThen(Conditions.cc).get).exists { op =>
      op === cc
    }).assert
  }

  test("ObserveEngine setSkyBackground should set Sky Background condition") {
    val sb = SkyBackground.Bright
    val s0 = EngineState.default[IO]
    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setSkyBackground(sb, UserDetails("", "")), 2)
    } yield sf.flatMap(EngineState.conditions.andThen(Conditions.sb).get).exists { op =>
      op === sb
    }).assert
  }

  // test("ObserveEngine setObserver should set observer's name") {
  //   val observer = Observer("Joe")
  //   val s0       = ODBSequencesLoader
  //     .loadSequenceEndo[IO](seqObsId1, sequence(seqObsId1), executeEngine)
  //     .apply(EngineState.default[IO])
  //   (for {
  //     oe <- observeEngine
  //     sf <-
  //       advanceN(oe, s0, oe.setObserver(seqObsId1, UserDetails("", ""), observer), 2)
  //   } yield sf
  //     .flatMap(
  //       EngineState
  //         .sequences[IO]
  //         .andThen(mapIndex[Observation.Id, SequenceData[IO]].index(seqObsId1))
  //         .getOption
  //     )
  //     .flatMap(_.observer)
  //     .exists { op =>
  //       op === observer
  //     }).assert
  // }

//   "ObserveEngine" should "not run 2nd sequence because it's using the same resource" in {
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
//         .replace(SequenceState.Running.init)).apply(EngineState.default[IO])
//
//     (for {
//       q  <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf <- advanceOne(
//               q,
//               s0,
//               observeEngine.start(q,
//                                   seqObsId2,
//                                   UserDetails("", ""),
//                                   Observer(""),
//                                   clientId,
//                                   RunOverride.Default
//               )
//             )
//     } yield inside(
//       sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId2).getOption).map(_.status)
//     ) { case Some(status) =>
//       assert(status.isIdle)
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
//         .replace(SequenceState.Running.init)).apply(EngineState.default[IO])
//
//     (for {
//       q  <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf <- advanceN(
//               q,
//               s0,
//               observeEngine.start(q,
//                                   seqObsId2,
//                                   UserDetails("", ""),
//                                   Observer(""),
//                                   clientId,
//                                   RunOverride.Default
//               ),
//               2
//             )
//     } yield inside(
//       sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId2).getOption).map(_.status)
//     ) { case Some(status) =>
//       assert(status.isRunning)
//     }).unsafeRunSync()
//   }
//
//   "ObserveEngine configSystem" should "run a system configuration" in {
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
//                                             UserDetails("", ""),
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
//         .replace(SequenceState.Running.init)).apply(EngineState.default[IO])
//
//     (for {
//       q  <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf <-
//         advanceOne(q,
//                    s0,
//                    observeEngine.configSystem(q,
//                                               seqObsId1,
//                                               Observer(""),
//                                               UserDetails("", ""),
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
//         .replace(SequenceState.Running.init)).apply(EngineState.default[IO])
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
//                                      UserDetails("", ""),
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
//         .replace(SequenceState.Running.init)).apply(EngineState.default[IO])
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
//                           UserDetails("", ""),
//                           stepId(1),
//                           Instrument.F2,
//                           clientId
//             ),
//           3
//         )
//     } yield inside(sf.flatMap(EngineState.sequences[IO].index(seqObsId2).getOption)) {
//       case Some(s) =>
//         assertResult(Some(Action.ActionState.Started))(
//           s.seqGen.configActionCoord(stepId(1), Instrument.F2).map(s.seq.getSingleState)
//         )
//     }).unsafeRunSync()
//   }
//
//   "ObserveEngine startFrom" should "start a sequence from an arbitrary step" in {
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
//       sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption).map(_.toSequence.steps)
//     ) { case Some(steps) =>
//       assertResult(Some(StepState.Skipped))(steps.get(0).map(_.status))
//       assertResult(Some(StepState.Skipped))(steps.get(1).map(_.status))
//       assertResult(Some(StepState.Completed))(steps.get(2).map(_.status))
//     }).unsafeRunSync()
//   }
//
//   "ObserveEngine startFrom" should "not start the sequence if there is a resource conflict" in {
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
//         .replace(SequenceState.Running.init)).apply(EngineState.default[IO])
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
//     } yield inside(
//       sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId2).getOption).map(_.status)
//     ) { case Some(status) =>
//       assert(status.isIdle)
//     }).unsafeRunSync()
//   }
//
//   private def testTargetSequence(
//     targetName:   String,
//     startStepIdx: Int,
//     obsClass:     List[ObsClass],
//     obsType:      List[String]
//   ): SequenceGen[IO] = {
//     val resources = Set(Instrument.GmosS, TCS)
//
//     SequenceGen[IO](
//       id = seqObsId1,
//       "GS-ENG20210713-1",
//       title = "",
//       instrument = Instrument.GmosS,
//       steps = obsClass.zip(obsType).zipWithIndex.map { case ((obC, obT), i) =>
//         SequenceGen.PendingStepGen(
//           stepId(startStepIdx + i),
//           Monoid.empty[DataId],
//           config = CleanConfig(
//             new DefaultConfig(),
//             Map(
//               (TELESCOPE_KEY / "Base:name", targetName),
//               (OBSERVE_KEY / OBS_CLASS_PROP, obC.headerValue()),
//               (OBSERVE_KEY / OBSERVE_TYPE_PROP, obT)
//             )
//           ),
//           resources = resources,
//           _ => InstrumentSystem.Uncontrollable,
//           generator = SequenceGen.StepActionsGen(
//             configs = resources.map(r => r -> { _: SystemOverrides => pendingAction[IO](r) }).toMap,
//             post = (_, _) => Nil
//           )
//         )
//       }
//     )
//   }
//
//   private def simpleSequenceWithTargetName(name: String): SequenceGen[IO] =
//     testTargetSequence(name, 1, List(ObsClass.SCIENCE), List(SCIENCE_OBSERVE_TYPE))
//
//   private def systemsWithTargetName(name: String): Systems[IO] =
//     defaultSystems.copy(tcsKeywordReader =
//       new DummyTcsKeywordsReader.DummyTcsKeywordReaderImpl[IO] {
//         override def sourceATarget: TargetKeywordsReader[IO] =
//           new DummyTargetKeywordsReader.DummyTargetKeywordsReaderImpl[IO] {
//             override def objectName: IO[String] = name.pure[IO]
//           }
//       }
//     )
//
//   "ObserveEngine start" should "start the sequence if it passes the target check" in {
//     val systems = systemsWithTargetName("proof")
//
//     val seq = simpleSequenceWithTargetName("proof")
//
//     val s0 = ODBSequencesLoader
//       .loadSequenceEndo[IO](seqObsId1, seq, executeEngine)
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf            <- advanceOne(
//                          q,
//                          s0,
//                          observeEngine.start(q,
//                                              seqObsId1,
//                                              UserDetails("", ""),
//                                              Observer(""),
//                                              clientId,
//                                              RunOverride.Default
//                          )
//                        )
//     } yield inside(
//       sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption).map(_.status)
//     ) { case Some(status) =>
//       assert(status.isRunning)
//     }).unsafeRunSync()
//   }
//
//   it should "not start the sequence if it fails the target check for science observations" in {
//     val systems = systemsWithTargetName("other")
//
//     val seq = testTargetSequence("proof", 1, List(ObsClass.SCIENCE), List(SCIENCE_OBSERVE_TYPE))
//
//     val s0 = ODBSequencesLoader
//       .loadSequenceEndo[IO](seqObsId1, seq, executeEngine)
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf            <- advanceOne(
//                          q,
//                          s0,
//                          observeEngine.start(q,
//                                              seqObsId1,
//                                              UserDetails("", ""),
//                                              Observer(""),
//                                              clientId,
//                                              RunOverride.Default
//                          )
//                        )
//     } yield inside(
//       sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption).map(_.status)
//     ) { case Some(status) =>
//       assert(status.isIdle)
//     }).unsafeRunSync()
//   }
//
//   it should "not start the sequence if it fails the target check for night calibrations" in {
//     val systems = systemsWithTargetName("other")
//
//     val seq = testTargetSequence("proof", 1, List(ObsClass.PROG_CAL), List(SCIENCE_OBSERVE_TYPE))
//
//     val s0 = ODBSequencesLoader
//       .loadSequenceEndo[IO](seqObsId1, seq, executeEngine)
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf            <- advanceOne(
//                          q,
//                          s0,
//                          observeEngine.start(q,
//                                              seqObsId1,
//                                              UserDetails("", ""),
//                                              Observer(""),
//                                              clientId,
//                                              RunOverride.Default
//                          )
//                        )
//     } yield inside(
//       sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption).map(_.status)
//     ) { case Some(status) =>
//       assert(status.isIdle)
//     }).unsafeRunSync()
//   }
//
//   it should "not start the sequence if it fails the target check for partner calibrations" in {
//     val systems = systemsWithTargetName("other")
//
//     val seq = testTargetSequence("proof", 1, List(ObsClass.PARTNER_CAL), List(SCIENCE_OBSERVE_TYPE))
//
//     val s0 = ODBSequencesLoader
//       .loadSequenceEndo[IO](seqObsId1, seq, executeEngine)
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf            <- advanceOne(
//                          q,
//                          s0,
//                          observeEngine.start(q,
//                                              seqObsId1,
//                                              UserDetails("", ""),
//                                              Observer(""),
//                                              clientId,
//                                              RunOverride.Default
//                          )
//                        )
//     } yield inside(
//       sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption).map(_.status)
//     ) { case Some(status) =>
//       assert(status.isIdle)
//     }).unsafeRunSync()
//   }
//
//   it should "pass the target check for ephemeris target" in {
//     val systems = systemsWithTargetName("proof")
//
//     val seq = testTargetSequence("proof.eph", 1, List(ObsClass.SCIENCE), List(SCIENCE_OBSERVE_TYPE))
//
//     val s0 = ODBSequencesLoader
//       .loadSequenceEndo[IO](seqObsId1, seq, executeEngine)
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf            <- advanceOne(
//                          q,
//                          s0,
//                          observeEngine.start(q,
//                                              seqObsId1,
//                                              UserDetails("", ""),
//                                              Observer(""),
//                                              clientId,
//                                              RunOverride.Default
//                          )
//                        )
//     } yield inside(
//       sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption).map(_.status)
//     ) { case Some(status) =>
//       assert(status.isRunning)
//     }).unsafeRunSync()
//   }
//
//   it should "start sequence that fails target check if forced" in {
//     val systems = systemsWithTargetName("other")
//
//     val seq = simpleSequenceWithTargetName("proof")
//
//     val s0 = ODBSequencesLoader
//       .loadSequenceEndo[IO](seqObsId1, seq, executeEngine)
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf            <- advanceOne(
//                          q,
//                          s0,
//                          observeEngine.start(q,
//                                              seqObsId1,
//                                              UserDetails("", ""),
//                                              Observer(""),
//                                              clientId,
//                                              RunOverride.Override
//                          )
//                        )
//     } yield inside(
//       sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption).map(_.status)
//     ) { case Some(status) =>
//       assert(status.isRunning)
//     }).unsafeRunSync()
//   }
//
//   it should "not check target for calibrations" in {
//     val systems = systemsWithTargetName("other")
//
//     val seq = simpleSequenceWithTargetName("proof")
//
//     val s0 = ODBSequencesLoader
//       .loadSequenceEndo[IO](seqObsId1, seq, executeEngine)
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf            <- advanceOne(
//                          q,
//                          s0,
//                          observeEngine.start(q,
//                                              seqObsId1,
//                                              UserDetails("", ""),
//                                              Observer(""),
//                                              clientId,
//                                              RunOverride.Override
//                          )
//                        )
//     } yield inside(
//       sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption).map(_.status)
//     ) { case Some(status) =>
//       assert(status.isRunning)
//     }).unsafeRunSync()
//   }
//
//   "ObserveEngine startFrom" should "start the sequence if it passes the target check" in {
//     val systems = systemsWithTargetName("proof")
//
//     val seq = testTargetSequence("proof",
//                                  1,
//                                  List(ObsClass.ACQ, ObsClass.SCIENCE),
//                                  List(ARC_OBSERVE_TYPE, SCIENCE_OBSERVE_TYPE)
//     )
//
//     val s0 = ODBSequencesLoader
//       .loadSequenceEndo[IO](seqObsId1, seq, executeEngine)
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf            <-
//         advanceOne(q,
//                    s0,
//                    observeEngine.startFrom(q,
//                                            seqObsId1,
//                                            Observer(""),
//                                            stepId(2),
//                                            clientId,
//                                            RunOverride.Default
//                    )
//         )
//     } yield inside(
//       sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption).map(_.status)
//     ) { case Some(status) =>
//       assert(status.isRunning)
//     }).unsafeRunSync()
//   }
//
//   it should "not start the sequence if it fails the target check" in {
//     val systems = systemsWithTargetName("other")
//
//     val seq = testTargetSequence("proof",
//                                  1,
//                                  List(ObsClass.ACQ, ObsClass.SCIENCE),
//                                  List(ARC_OBSERVE_TYPE, SCIENCE_OBSERVE_TYPE)
//     )
//
//     val s0 = ODBSequencesLoader
//       .loadSequenceEndo[IO](seqObsId1, seq, executeEngine)
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       result        <-
//         observeEngine.startFrom(q,
//                                 seqObsId1,
//                                 Observer(""),
//                                 stepId(2),
//                                 clientId,
//                                 RunOverride.Default
//         ) *>
//           observeEngine.stream(Stream.fromQueueUnterminated(q))(s0).take(1).compile.last
//     } yield inside(result) { case Some((out, sf)) =>
//       inside(EngineState.sequenceStateIndex[IO](seqObsId1).getOption(sf).map(_.status)) {
//         case Some(status) => assert(status.isIdle)
//       }
//       inside(out) {
//         case UserCommandResponse(_,
//                                  Outcome.Ok,
//                                  Some(
//                                    RequestConfirmation(
//                                      UserPrompt.ChecksOverride(_, stpid, _, _),
//                                      _
//                                    )
//                                  )
//             ) =>
//           assert(stpid === stepId(2))
//       }
//     }).unsafeRunSync()
//   }
//
//   it should "start the sequence that fails target check if forced" in {
//     val systems = systemsWithTargetName("other")
//
//     val seq = testTargetSequence("proof",
//                                  1,
//                                  List(ObsClass.ACQ, ObsClass.SCIENCE),
//                                  List(ARC_OBSERVE_TYPE, SCIENCE_OBSERVE_TYPE)
//     )
//
//     val s0 = ODBSequencesLoader
//       .loadSequenceEndo[IO](seqObsId1, seq, executeEngine)
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf            <-
//         advanceOne(q,
//                    s0,
//                    observeEngine.startFrom(q,
//                                            seqObsId1,
//                                            Observer(""),
//                                            stepId(2),
//                                            clientId,
//                                            RunOverride.Override
//                    )
//         )
//     } yield inside(sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption)) {
//       case Some(s) =>
//         assert(s.status.isRunning)
//         inside(s.currentStep) { case Some(t) =>
//           assert(t.id === stepId(2))
//         }
//     }).unsafeRunSync()
//   }
//
//   "ObserveEngine startFrom" should "not check target for calibrations" in {
//     val systems = systemsWithTargetName("other")
//
//     val seq = testTargetSequence("proof",
//                                  1,
//                                  List(ObsClass.DAY_CAL, ObsClass.DAY_CAL),
//                                  List(ARC_OBSERVE_TYPE, DARK_OBSERVE_TYPE)
//     )
//
//     val s0 = ODBSequencesLoader
//       .loadSequenceEndo[IO](seqObsId1, seq, executeEngine)
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf            <-
//         advanceOne(q,
//                    s0,
//                    observeEngine.startFrom(q,
//                                            seqObsId1,
//                                            Observer(""),
//                                            stepId(2),
//                                            clientId,
//                                            RunOverride.Default
//                    )
//         )
//     } yield inside(
//       sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption).map(_.status)
//     ) { case Some(status) =>
//       assert(status.isRunning)
//     }).unsafeRunSync()
//   }
//
//   private val testConditionsSequence: SequenceGen[IO] = {
//     val resources         = Set(Instrument.GmosS, TCS)
//     val obsClass          = List(ObsClass.PROG_CAL, ObsClass.SCIENCE)
//     val obsType           = List(DARK_OBSERVE_TYPE, SCIENCE_OBSERVE_TYPE)
//     val startStepIdx      = 1
//     val ObsConditionsProp = "obsConditions"
//     val reqConditions     = Map(
//       OCS_KEY / ObsConditionsProp / WATER_VAPOR_PROP    -> "20",
//       OCS_KEY / ObsConditionsProp / SKY_BACKGROUND_PROP -> "20",
//       OCS_KEY / ObsConditionsProp / IMAGE_QUALITY_PROP  -> "20",
//       OCS_KEY / ObsConditionsProp / CLOUD_COVER_PROP    -> "50"
//     )
//
//     SequenceGen[IO](
//       id = seqObsId1,
//       name = "GS-ENG20210713-1",
//       title = "",
//       instrument = Instrument.GmosS,
//       steps = obsClass.zip(obsType).zipWithIndex.map { case ((obC, obT), i) =>
//         SequenceGen.PendingStepGen(
//           stepId(startStepIdx + i),
//           Monoid.empty[DataId],
//           config = CleanConfig(
//             new DefaultConfig(),
//             Map(
//               (OBSERVE_KEY / OBS_CLASS_PROP, obC.headerValue()),
//               (OBSERVE_KEY / OBSERVE_TYPE_PROP, obT)
//             ) ++ reqConditions
//           ),
//           resources = resources,
//           _ => InstrumentSystem.Uncontrollable,
//           generator = SequenceGen.StepActionsGen(
//             configs = resources.map(r => r -> { _: SystemOverrides => pendingAction[IO](r) }).toMap,
//             post = (_, _) => Nil
//           )
//         )
//       }
//     )
//   }
//
//   "ObserveEngine start" should "start the sequence if it passes the conditions check" in {
//
//     val seq = testConditionsSequence
//
//     val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](seqObsId1, seq, executeEngine) >>>
//       Focus[EngineState](_.conditions[IO])[IO]
//         .andThen(Conditions.iq)
//         .replace(ImageQuality.Percent20) >>>
//       Focus[EngineState](_.conditions[IO])[IO]
//         .andThen(Conditions.wv)
//         .replace(WaterVapor.Percent20) >>>
//       Focus[EngineState](_.conditions[IO])[IO]
//         .andThen(Conditions.sb)
//         .replace(SkyBackground.Percent20) >>>
//       Focus[EngineState](_.conditions[IO])[IO].andThen(Conditions.cc).replace(CloudCover.Percent50))
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, defaultSystems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf            <- advanceOne(
//                          q,
//                          s0,
//                          observeEngine.start(q,
//                                              seqObsId1,
//                                              UserDetails("", ""),
//                                              Observer(""),
//                                              clientId,
//                                              RunOverride.Default
//                          )
//                        )
//     } yield inside(sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption)) {
//       case Some(s) =>
//         assert(s.status.isRunning)
//         inside(s.currentStep) { case Some(t) =>
//           assert(t.id === stepId(1))
//         }
//     }).unsafeRunSync()
//   }
//
//   it should "not start the sequence if it fails the conditions check" in {
//     val seq = testConditionsSequence
//
//     val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](seqObsId1, seq, executeEngine) >>>
//       Focus[EngineState](_.conditions[IO])[IO]
//         .andThen(Conditions.iq)
//         .replace(ImageQuality.Percent70) >>>
//       Focus[EngineState](_.conditions[IO])[IO]
//         .andThen(Conditions.wv)
//         .replace(WaterVapor.Percent20) >>>
//       Focus[EngineState](_.conditions[IO])[IO]
//         .andThen(Conditions.sb)
//         .replace(SkyBackground.Percent20) >>>
//       Focus[EngineState](_.conditions[IO])[IO].andThen(Conditions.cc).replace(CloudCover.Percent50))
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, defaultSystems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       result        <-
//         observeEngine.start(q,
//                             seqObsId1,
//                             UserDetails("", ""),
//                             Observer(""),
//                             clientId,
//                             RunOverride.Default
//         ) *>
//           observeEngine.stream(Stream.fromQueueUnterminated(q))(s0).take(1).compile.last
//     } yield inside(result) { case Some((out, sf)) =>
//       inside(EngineState.sequenceStateIndex[IO](seqObsId1).getOption(sf).map(_.status)) {
//         case Some(status) => assert(status.isIdle)
//       }
//       inside(out) {
//         case UserCommandResponse(_,
//                                  Outcome.Ok,
//                                  Some(
//                                    RequestConfirmation(
//                                      UserPrompt.ChecksOverride(_, stpid, _, _),
//                                      _
//                                    )
//                                  )
//             ) =>
//           assert(stpid === stepId(1))
//       }
//     }).unsafeRunSync()
//   }
//
//   it should "start the sequence that fails conditions check if forced" in {
//
//     val seq = testConditionsSequence
//
//     val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](seqObsId1, seq, executeEngine) >>>
//       Focus[EngineState](_.conditions[IO])[IO]
//         .andThen(Conditions.iq)
//         .replace(ImageQuality.Percent70) >>>
//       Focus[EngineState](_.conditions[IO])[IO]
//         .andThen(Conditions.wv)
//         .replace(WaterVapor.Percent20) >>>
//       Focus[EngineState](_.conditions[IO])[IO]
//         .andThen(Conditions.sb)
//         .replace(SkyBackground.Percent20) >>>
//       Focus[EngineState](_.conditions[IO])[IO].andThen(Conditions.cc).replace(CloudCover.Percent50))
//       .apply(EngineState.default[IO])
//
//     (for {
//       sm            <- ObserveMetrics.build[IO](Site.GS, new CollectorRegistry())
//       observeEngine <- ObserveEngine.build(Site.GS, defaultSystems, defaultSettings, sm)
//       q             <- Queue.bounded[IO, executeEngine.EventType](10)
//       sf            <-
//         advanceN(
//           q,
//           s0,
//           observeEngine
//             .start(q, seqObsId1, UserDetails("", ""), Observer(""), clientId, RunOverride.Override),
//           3
//         )
//     } yield inside(sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption)) {
//       case Some(s) =>
//         assert(s.status.isRunning)
//         inside(s.currentStep) { case Some(t) =>
//           assert(t.id === stepId(1))
//         }
//     }).unsafeRunSync()
//   }
//
}
