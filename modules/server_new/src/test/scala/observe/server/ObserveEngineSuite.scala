// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Monoid
import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all.*
import eu.timepit.refined.cats.given
import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.enums.Instrument
import lucuma.core.enums.*
import lucuma.core.math.Offset
import lucuma.core.model.ConstraintSet
import lucuma.core.model.ElevationRange
import lucuma.core.model.Program
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.ExecutionConfig
import lucuma.core.model.sequence.ExecutionSequence
import lucuma.core.model.sequence.InstrumentExecutionConfig.GmosNorth
import lucuma.core.model.sequence.Step
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.StepEstimate
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.StaticConfig
import lucuma.refined.*
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.Execution
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation as ODBObservation
import observe.common.test.*
import observe.engine.EventResult
import observe.engine.EventResult.Outcome
import observe.engine.Sequence
import observe.engine.user
import observe.model.ClientId
import observe.model.Conditions
import observe.model.Observer
import observe.model.Operator
import observe.model.SequenceState
import observe.model.StepState
import observe.model.SystemOverrides
import observe.model.UserPrompt
import observe.model.dhs.DataId
import observe.model.enums.Resource
import observe.model.enums.Resource.Gcal
import observe.model.enums.Resource.TCS
import observe.model.enums.RunOverride
import observe.server.SeqEvent.RequestConfirmation
import observe.server.SequenceGen.StepStatusGen

import java.util.UUID

class ObserveEngineSuite extends TestCommon {

  import TestCommon.*

  val clientId = ClientId(UUID.randomUUID())

  test("ObserveEngine setOperator should set operator's name") {
    val operator = Operator("Joe".refined)
    val s0       = EngineState.default[IO]
    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setOperator(user, operator), 2)
    } yield sf.flatMap(EngineState.operator.get).exists { op =>
      op === operator
    }).assert
  }

  test("ObserveEngine setImageQuality should set Image Quality condition") {
    val iq = ImageQuality.PointEight
    val s0 = EngineState.default[IO]

    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setImageQuality(iq, user, clientId), 2)
    } yield sf.flatMap(EngineState.conditions.andThen(Conditions.iq).get).exists { op =>
      op === iq
    }).assert

  }

  test("ObserveEngine setWaterVapor should set Water Vapor condition") {
    val wv = WaterVapor.VeryDry
    val s0 = EngineState.default[IO]
    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setWaterVapor(wv, user, clientId), 2)
    } yield sf.flatMap(EngineState.conditions.andThen(Conditions.wv).get).exists { op =>
      op === wv
    }).assert
  }

  test("ObserveEngine setCloudExtinction should set Cloud Extinction condition") {
    val ce = CloudExtinction.TwoPointZero
    val s0 = EngineState.default[IO]
    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setCloudExtinction(ce, user, clientId), 2)
    } yield sf.flatMap(EngineState.conditions.andThen(Conditions.ce).get).exists { op =>
      op === ce
    }).assert
  }

  test("ObserveEngine setSkyBackground should set Sky Background condition") {
    val sb = SkyBackground.Bright
    val s0 = EngineState.default[IO]
    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setSkyBackground(sb, user, clientId), 2)
    } yield sf.flatMap(EngineState.conditions.andThen(Conditions.sb).get).exists { op =>
      op === sb
    }).assert
  }

  test("ObserveEngine setObserver should set observer's name") {
    val observer = Observer("Joe".refined)
    val s0       = ODBSequencesLoader
      .loadSequenceEndo[IO](
        None,
        sequence(seqObsId1),
        EngineState.instrumentLoaded(Instrument.GmosNorth)
      )
      .apply(EngineState.default[IO])
    (for {
      oe <- observeEngine
      sf <-
        advanceN(oe, s0, oe.setObserver(seqObsId1, user, observer), 2)
    } yield sf
      .flatMap(EngineState.atSequence(seqObsId1).getOption)
      .flatMap(_.observer)
      .exists { op =>
        op === observer
      }).assert
  }

  test("ObserveEngine should not run 2nd sequence because it's using the same resource") {
    val s0 = (
      ODBSequencesLoader.loadSequenceEndo[IO](
        None,
        sequenceWithResources(seqObsId1, Instrument.GmosNorth, Set(Instrument.GmosNorth, TCS)),
        EngineState.instrumentLoaded(Instrument.GmosNorth)
      ) >>>
        ODBSequencesLoader.loadSequenceEndo[IO](
          None,
          sequenceWithResources(seqObsId2, Instrument.GmosSouth, Set(Instrument.GmosSouth, TCS)),
          EngineState.instrumentLoaded(Instrument.GmosSouth)
        ) >>>
        EngineState
          .sequenceStateIndex[IO](seqObsId1)
          .andThen(Sequence.State.status[IO])
          .replace(SequenceState.Running.Init)
    ).apply(EngineState.default[IO])

    (for {
      oe <- observeEngine
      sf <- advanceOne(
              oe,
              s0,
              oe.start(seqObsId2, user, Observer("Joe".refined), clientId, RunOverride.Default)
            )
    } yield sf
      .flatMap(EngineState.sequenceStateIndex[IO](seqObsId2).getOption)
      .exists(_.status.isIdle)).assert
  }

  test("ObserveEngine should run 2nd sequence when there are no shared resources") {
    val s0 = (
      ODBSequencesLoader.loadSequenceEndo[IO](
        None,
        sequenceWithResources(seqObsId1, Instrument.GmosNorth, Set(Instrument.GmosNorth, TCS)),
        EngineState.instrumentLoaded(Instrument.GmosNorth)
      ) >>>
        ODBSequencesLoader.loadSequenceEndo[IO](
          None,
          sequenceWithResources(seqObsId2, Instrument.GmosSouth, Set(Instrument.GmosSouth)),
          EngineState.instrumentLoaded(Instrument.GmosSouth)
        ) >>>
        EngineState
          .sequenceStateIndex[IO](seqObsId1)
          .andThen(Sequence.State.status[IO])
          .replace(SequenceState.Running.Init)
    ).apply(EngineState.default[IO])

    (for {
      oe <- observeEngine
      sf <- advanceN(
              oe,
              s0,
              oe.start(
                seqObsId2,
                user,
                Observer("Joe".refined),
                clientId,
                RunOverride.Default
              ),
              2
            )
    } yield sf
      .flatMap(EngineState.sequenceStateIndex[IO](seqObsId2).getOption)
      .exists(_.status.isRunning)).assert
  }

  test("ObserveEngine configSystem should run a system configuration") {
    val s0 = ODBSequencesLoader
      .loadSequenceEndo[IO](
        None,
        sequenceWithResources(seqObsId1, Instrument.GmosNorth, Set(Instrument.GmosNorth, TCS)),
        EngineState.instrumentLoaded(Instrument.GmosNorth)
      )
      .apply(EngineState.default[IO])

    (for {
      oe <- observeEngine
      sf <- advanceN(
              oe,
              s0,
              oe.configSystem(
                seqObsId1,
                Observer("Joe".refined),
                user,
                stepId(1),
                TCS,
                clientId
              ),
              3
            )
    } yield sf
      .flatMap(
        EngineState.atSequence(seqObsId1).getOption
      )
      .flatMap(s => s.seqGen.configActionCoord(stepId(1), TCS).map(s.seq.getSingleState))
      .exists(_.started)).assert
  }

  test("ObserveEngine should not run a system configuration if sequence is running") {
    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      None,
      sequenceWithResources(seqObsId1, Instrument.GmosNorth, Set(Instrument.GmosNorth, TCS)),
      EngineState.instrumentLoaded(Instrument.GmosNorth)
    ) >>>
      EngineState
        .sequenceStateIndex[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.Init)).apply(EngineState.default[IO])

    (for {
      oe <- observeEngine
      sf <- advanceOne(
              oe,
              s0,
              oe.configSystem(
                seqObsId1,
                Observer("Joe".refined),
                user,
                stepId(1),
                TCS,
                clientId
              )
            )
    } yield sf
      .flatMap(
        EngineState.atSequence(seqObsId1).getOption
      )
      .flatMap(s => s.seqGen.configActionCoord(stepId(1), TCS).map(s.seq.getSingleState))
      .exists(_.isIdle)).assert
  }

  test("ObserveEngine should not run a system configuration if system is in use") {
    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      None,
      sequenceWithResources(seqObsId1, Instrument.GmosNorth, Set(Instrument.GmosNorth, TCS)),
      EngineState.instrumentLoaded(Instrument.GmosNorth)
    ) >>>
      ODBSequencesLoader.loadSequenceEndo[IO](
        None,
        sequenceWithResources(seqObsId2, Instrument.GmosSouth, Set(Instrument.GmosSouth, TCS)),
        EngineState.instrumentLoaded(Instrument.GmosSouth)
      ) >>>
      EngineState
        .sequenceStateIndex[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.Init)).apply(EngineState.default[IO])

    (for {
      oe <- observeEngine
      sf <-
        advanceOne(
          oe,
          s0,
          oe.configSystem(
            seqObsId2,
            Observer("Joe".refined),
            user,
            stepId(1),
            TCS,
            clientId
          )
        )
    } yield sf
      .flatMap(
        EngineState
          .atSequence(seqObsId2)
          .getOption
      )
      .flatMap(s => s.seqGen.configActionCoord(stepId(1), TCS).map(s.seq.getSingleState))
      .exists(_.isIdle)).assert
  }

  test(
    "ObserveEngine should run a system configuration when other sequence is running with other systems"
  ) {
    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      None,
      sequenceWithResources(seqObsId1, Instrument.GmosNorth, Set(Instrument.GmosNorth, TCS)),
      EngineState.instrumentLoaded(Instrument.GmosNorth)
    ) >>>
      ODBSequencesLoader.loadSequenceEndo[IO](
        None,
        sequenceWithResources(seqObsId2, Instrument.GmosSouth, Set(Instrument.GmosNorth, Gcal)),
        EngineState.instrumentLoaded(Instrument.GmosSouth)
      ) >>>
      EngineState
        .sequenceStateIndex[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.Init)).apply(EngineState.default[IO])

    (for {
      oe <- observeEngine
      sf <-
        advanceN(
          oe,
          s0,
          oe
            .configSystem(
              seqObsId2,
              Observer("Joe".refined),
              user,
              stepId(1),
              Gcal,
              clientId
            ),
          3
        )
    } yield sf
      .flatMap(EngineState.atSequence(seqObsId2).getOption)
      .flatMap(s => s.seqGen.configActionCoord(stepId(1), Gcal).map(s.seq.getSingleState))
      .exists(_.started)).assert
  }

  test("ObserveEngine startFrom should start a sequence from an arbitrary step") {
    val s0        = ODBSequencesLoader
      .loadSequenceEndo[IO](
        None,
        sequenceNSteps(seqObsId1, 5),
        EngineState.instrumentLoaded(Instrument.GmosNorth)
      )
      .apply(EngineState.default[IO])
    val runStepId = stepId(3)

    (for {
      oe <- observeEngine
      _  <- oe.startFrom(
              seqObsId1,
              Observer("Joe".refined),
              runStepId,
              clientId,
              RunOverride.Default
            )
      sf <- oe
              .stream(s0)
              .map(_._2)
              .takeThrough(_.sequences.values.exists(_.seq.status.isRunning))
              .compile
              .last
    } yield sf
      .flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption)
      .map(_.toSequence.steps)).map { s =>
      assert(s.flatMap(_.get(0)).exists(_.status === StepState.Skipped))
      assert(s.flatMap(_.get(1)).exists(_.status === StepState.Skipped))
      assert(s.flatMap(_.get(2)).exists(_.status === StepState.Completed))
    }
  }

  test("ObserveEngine startFrom should not start the sequence if there is a resource conflict") {
    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      None,
      sequenceWithResources(seqObsId1, Instrument.GmosNorth, Set(Instrument.GmosNorth, TCS)),
      EngineState.instrumentLoaded(Instrument.GmosNorth)
    ) >>>
      ODBSequencesLoader.loadSequenceEndo[IO](
        None,
        sequenceWithResources(seqObsId2, Instrument.GmosSouth, Set(Instrument.GmosSouth, TCS)),
        EngineState.instrumentLoaded(Instrument.GmosSouth)
      ) >>>
      EngineState
        .sequenceStateIndex[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.Init)).apply(EngineState.default[IO])

    val runStepId = stepId(2)

    (for {
      oe <- observeEngine
      _  <- oe.startFrom(
              seqObsId2,
              Observer("Joe".refined),
              runStepId,
              clientId,
              RunOverride.Default
            )
      sf <- oe
              .stream(s0)
              .map(_._2)
              .takeThrough(_.sequences.get(seqObsId2).exists(_.seq.status.isRunning))
              .compile
              .last
    } yield sf
      .flatMap(EngineState.sequenceStateIndex[IO](seqObsId2).getOption)
      .exists(_.status.isIdle)).assert
  }

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
//   test("ObserveEngine start should start the sequence if it passes the target check") {
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
//                                              user,
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
//                                              user,
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
//                                              user,
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
//                                              user,
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
//                                              user,
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
//                                              user,
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
//                                              user,
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

  private val testConditionsSequence: SequenceGen[IO] = {
    val resources: Set[Resource | Instrument] = Set(Instrument.GmosNorth, TCS)

    val obsTypes: NonEmptyList[(ObserveClass, StepConfig)] = NonEmptyList(
      (ObserveClass.ProgramCal, StepConfig.Dark),
      List((ObserveClass.Science, StepConfig.Science(Offset.Zero, GuideState.Enabled)))
    )
    val startStepIdx                                       = 1
    val reqConditions                                      = ConstraintSet(
      ImageQuality.PointTwo,
      CloudExtinction.PointFive,
      SkyBackground.Dark,
      WaterVapor.Median,
      ElevationRange.HourAngle.Default
    )

    val stepList: NonEmptyList[Step[DynamicConfig.GmosNorth]] = obsTypes.zipWithIndex.map {
      case ((cl, st), idx) =>
        Step[DynamicConfig.GmosNorth](
          stepId(idx + startStepIdx),
          dynamicCfg1,
          st,
          StepEstimate.Zero,
          cl,
          Breakpoint.Disabled
        )
    }

    SequenceGen[IO](
      ODBObservation(
        id = seqObsId1,
        title = "",
        ObsStatus.Ready,
        ObsActiveStatus.Active,
        ODBObservation.Program(
          Program.Id(PosLong.unsafeFrom(123)),
          None
        ),
        TargetEnvironment(None),
        reqConditions,
        List.empty,
        Execution(
          GmosNorth(
            ExecutionConfig[StaticConfig.GmosNorth, DynamicConfig.GmosNorth](
              staticCfg1,
              ExecutionSequence[DynamicConfig.GmosNorth](
                Atom[DynamicConfig.GmosNorth](
                  atomId1,
                  None,
                  stepList
                ),
                List.empty,
                false
              ).some,
              None
            )
          ).some
        )
      ),
      instrument = Instrument.GmosNorth,
      SequenceType.Science,
      staticCfg1,
      atomId1,
      steps = stepList.map { step =>
        SequenceGen.PendingStepGen(
          step.id,
          Monoid.empty[DataId],
          resources = resources,
          _ => InstrumentSystem.Uncontrollable,
          generator = SequenceGen.StepActionsGen(
            configs =
              resources.map(r => r -> { (_: SystemOverrides) => pendingAction[IO](r) }).toMap,
            post = (_, _) => Nil
          ),
          StepStatusGen.Null,
          step.instrumentConfig,
          step.stepConfig,
          breakpoint = Breakpoint.Disabled
        )
      }.toList
    )
  }

  test("ObserveEngine start should start the sequence if it passes the conditions check") {

    val seq = testConditionsSequence

    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      None,
      seq,
      EngineState.instrumentLoaded(Instrument.GmosNorth)
    ) >>>
      EngineState.conditions.andThen(Conditions.iq).replace(ImageQuality.PointTwo.some) >>>
      EngineState.conditions.andThen(Conditions.wv).replace(WaterVapor.Median.some) >>>
      EngineState.conditions.andThen(Conditions.sb).replace(SkyBackground.Dark.some) >>>
      EngineState.conditions.andThen(Conditions.ce).replace(CloudExtinction.PointFive.some))
      .apply(EngineState.default[IO])

    (for {
      systems       <- defaultSystems
      observeEngine <- ObserveEngine.build[IO](Site.GN, systems, defaultSettings)
      sf            <- advanceOne(
                         observeEngine,
                         s0,
                         observeEngine.start(
                           seqObsId1,
                           user,
                           Observer("Joe".refined),
                           clientId,
                           RunOverride.Default
                         )
                       )
    } yield sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption)).map { s =>
      assert(s.exists(_.status.isRunning))
      assert(s.flatMap(_.currentStep).exists(_.id === stepId(1)))
    }
  }

  test("ObserveEngine start should not start the sequence if it fails the conditions check") {
    val seq = testConditionsSequence

    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      None,
      seq,
      EngineState.instrumentLoaded(Instrument.GmosNorth)
    ) >>>
      EngineState.conditions.andThen(Conditions.iq).replace(ImageQuality.OnePointZero.some) >>>
      EngineState.conditions.andThen(Conditions.wv).replace(WaterVapor.Dry.some) >>>
      EngineState.conditions.andThen(Conditions.sb).replace(SkyBackground.Darkest.some) >>>
      EngineState.conditions.andThen(Conditions.ce).replace(CloudExtinction.OnePointZero.some))
      .apply(EngineState.default[IO])

    for {
      systems       <- defaultSystems
      observeEngine <- ObserveEngine.build(Site.GN, systems, defaultSettings)
      result        <-
        observeEngine.start(
          seqObsId1,
          user,
          Observer("Joe".refined),
          clientId,
          RunOverride.Default
        ) *>
          observeEngine.stream(s0).take(1).compile.last
    } yield result
      .map { case (out, sf) =>
        assert(EngineState.sequenceStateIndex[IO](seqObsId1).getOption(sf).exists(_.status.isIdle))
        assert(out match {
          case EventResult.UserCommandResponse(
                _,
                Outcome.Ok,
                Some(
                  RequestConfirmation(
                    UserPrompt.ChecksOverride(_, stpid, _),
                    _
                  )
                )
              ) =>
            stpid === stepId(1)
          case _ => false
        })
      }
      .getOrElse(fail("Running ObserveEngine produced no output"))
  }

  test("ObserveEngine start should start the sequence that fails conditions check if forced") {

    val seq = testConditionsSequence

    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      None,
      seq,
      EngineState.instrumentLoaded(Instrument.GmosNorth)
    ) >>>
      EngineState.conditions.andThen(Conditions.iq).replace(ImageQuality.OnePointZero.some) >>>
      EngineState.conditions.andThen(Conditions.wv).replace(WaterVapor.Dry.some) >>>
      EngineState.conditions.andThen(Conditions.sb).replace(SkyBackground.Darkest.some) >>>
      EngineState.conditions.andThen(Conditions.ce).replace(CloudExtinction.OnePointZero.some))
      .apply(EngineState.default[IO])

    for {
      systems       <- defaultSystems
      observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings)
      sf            <-
        advanceN(
          observeEngine,
          s0,
          observeEngine
            .start(seqObsId1, user, Observer("Joe".refined), clientId, RunOverride.Override),
          3
        )
    } yield {
      assert(
        sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption)
          .exists(_.status.isRunning)
      )
      assert(
        sf.flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption)
          .flatMap(_.currentStep)
          .exists(_.id === stepId(1))
      )
    }
  }

}
