// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Monoid
import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all.*
import eu.timepit.refined.cats.given
import eu.timepit.refined.types.numeric.NonNegShort
import eu.timepit.refined.types.numeric.PosLong
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.enums.*
import lucuma.core.enums.Instrument
import lucuma.core.math.Offset
import lucuma.core.model.ConstraintSet
import lucuma.core.model.ElevationRange
import lucuma.core.model.Program
import lucuma.core.model.Target
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
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation as ODBObservation
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.Execution
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment.FirstScienceTarget
import observe.common.test.*
import observe.engine.EventResult
import observe.engine.EventResult.Outcome
import observe.engine.Sequence
import observe.engine.user
import observe.model
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
import observe.server.odb.TestOdbProxy
import observe.server.odb.TestOdbProxy.StepStartStep
import observe.server.tcs.DummyTargetKeywordsReader
import observe.server.tcs.DummyTcsKeywordsReader
import observe.server.tcs.TargetKeywordsReader

import java.util.UUID

class ObserveEngineSuite extends TestCommon {

  import TestCommon.*

  val clientId: model.ClientId.Type = ClientId(UUID.randomUUID())

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
      sf <- advanceN(
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
      db <- TestOdbProxy.build[IO](staticCfg1.some, List.empty, List.empty)
      oe <- observeEngineWithODB(db)
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

  private def testTargetSequence(targetName: NonEmptyString): SequenceGen[IO] = {
    val resources: Set[Resource | Instrument] = Set(Instrument.GmosNorth, TCS)

    val obsTypes: NonEmptyList[(ObserveClass, StepConfig)] = NonEmptyList(
      (ObserveClass.ProgramCal, StepConfig.Dark),
      List((ObserveClass.Science, StepConfig.Science(Offset.Zero, StepGuideState.Enabled)))
    )

    val startStepIdx  = 1
    val reqConditions = ConstraintSet(
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
        TargetEnvironment(Some(FirstScienceTarget(Target.Id.fromLong(1).get, targetName))),
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
      staticCfg1,
      SequenceGen.AtomGen(
        atomId1,
        SequenceType.Science,
        steps = stepList.map { step =>
          SequenceGen.PendingStepGen(
            step.id,
            Monoid.empty[DataId],
            resources = resources,
            _ => InstrumentSystem.Uncontrollable,
            generator = SequenceGen.StepActionsGen(
              odbAction[IO],
              odbAction[IO],
              configs =
                resources.map(r => r -> { (_: SystemOverrides) => pendingAction[IO](r) }).toMap,
              odbAction[IO],
              odbAction[IO],
              post = (_, _) => Nil,
              odbAction[IO],
              odbAction[IO]
            ),
            StepStatusGen.Null,
            step.instrumentConfig,
            step.stepConfig,
            breakpoint = Breakpoint.Disabled
          )
        }.toList
      )
    )
  }

  private def systemsWithTargetName(name: String): IO[Systems[IO]] =
    defaultSystems.map(
      _.copy(tcsKeywordReader = new DummyTcsKeywordsReader.DummyTcsKeywordReaderImpl[IO] {
        override def sourceATarget: TargetKeywordsReader[IO] =
          new DummyTargetKeywordsReader.DummyTargetKeywordsReaderImpl[IO] {
            override def objectName: IO[String] = name.pure[IO]
          }
      })
    )

  test("ObserveEngine start should start the sequence if it passes the target check") {
    val seq = testTargetSequence("proof".refined)

    val s0 = ODBSequencesLoader
      .loadSequenceEndo[IO](
        None,
        seq,
        EngineState.instrumentLoaded(Instrument.GmosNorth)
      )
      .apply(EngineState.default[IO])

    (for {
      systems <- systemsWithTargetName("proof")
      oe      <- ObserveEngine.build(Site.GN, systems, defaultSettings)
      sf      <- advanceOne(oe,
                            s0,
                            oe.start(
                              seqObsId1,
                              user,
                              Observer("Joe".refined),
                              clientId,
                              RunOverride.Default
                            )
                 )
    } yield sf
      .flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption)
      .exists(_.status.isRunning)).assert
  }

  test(
    "ObserveEngine start should not start the sequence if it fails the target check for science observations"
  ) {
    val seq = testTargetSequence("proof".refined)

    val s0 = ODBSequencesLoader
      .loadSequenceEndo[IO](
        None,
        seq,
        EngineState.instrumentLoaded(Instrument.GmosNorth)
      )
      .apply(EngineState.default[IO])

    (for {
      systems <- systemsWithTargetName("proof1")
      oe      <- ObserveEngine.build(Site.GN, systems, defaultSettings)
      sf      <- advanceOne(oe,
                            s0,
                            oe.start(
                              seqObsId1,
                              user,
                              Observer("Joe".refined),
                              clientId,
                              RunOverride.Default
                            )
                 )
    } yield sf
      .flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption)
      .exists(_.status.isIdle)).assert
  }

  test(
    "ObserveEngine start should start the sequence that fails the target check for if forced"
  ) {
    val seq = testTargetSequence("proof".refined)

    val s0 = ODBSequencesLoader
      .loadSequenceEndo[IO](
        None,
        seq,
        EngineState.instrumentLoaded(Instrument.GmosNorth)
      )
      .apply(EngineState.default[IO])

    (for {
      systems <- systemsWithTargetName("proof1")
      oe      <- ObserveEngine.build(Site.GN, systems, defaultSettings)
      sf      <- advanceOne(oe,
                            s0,
                            oe.start(
                              seqObsId1,
                              user,
                              Observer("Joe".refined),
                              clientId,
                              RunOverride.Override
                            )
                 )
    } yield sf
      .flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption)
      .exists(_.status.isRunning)).assert
  }

  test("ObserveEngine start should startFrom the sequence if it passes the target check") {
    val seq = testTargetSequence("proof".refined)

    val s0 = ODBSequencesLoader
      .loadSequenceEndo[IO](
        None,
        seq,
        EngineState.instrumentLoaded(Instrument.GmosNorth)
      )
      .apply(EngineState.default[IO])

    (for {
      systems <- systemsWithTargetName("proof")
      oe      <- ObserveEngine.build(Site.GN, systems, defaultSettings)
      sf      <- advanceOne(oe,
                            s0,
                            oe.startFrom(
                              seqObsId1,
                              Observer("Joe".refined),
                              stepId(2),
                              clientId,
                              RunOverride.Default
                            )
                 )
    } yield sf
      .flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption)
      .exists(_.status.isRunning)).assert
  }

  test(
    "ObserveEngine start should not startFrom the sequence if it doesn't pass the target check"
  ) {
    val seq = testTargetSequence("proof".refined)

    val s0 = ODBSequencesLoader
      .loadSequenceEndo[IO](
        None,
        seq,
        EngineState.instrumentLoaded(Instrument.GmosNorth)
      )
      .apply(EngineState.default[IO])

    (for {
      systems <- systemsWithTargetName("proof1")
      oe      <- ObserveEngine.build(Site.GN, systems, defaultSettings)
      sf      <- advanceOne(oe,
                            s0,
                            oe.startFrom(
                              seqObsId1,
                              Observer("Joe".refined),
                              stepId(2),
                              clientId,
                              RunOverride.Default
                            )
                 )
    } yield sf
      .flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption)
      .exists(_.status.isIdle)).assert
  }

  test(
    "ObserveEngine start should startFrom the sequence if it doesn't pass the target check but forced"
  ) {
    val seq = testTargetSequence("proof".refined)

    val s0 = ODBSequencesLoader
      .loadSequenceEndo[IO](
        None,
        seq,
        EngineState.instrumentLoaded(Instrument.GmosNorth)
      )
      .apply(EngineState.default[IO])

    (for {
      systems <- systemsWithTargetName("proof1")
      oe      <- ObserveEngine.build(Site.GN, systems, defaultSettings)
      sf      <- advanceOne(oe,
                            s0,
                            oe.startFrom(
                              seqObsId1,
                              Observer("Joe".refined),
                              stepId(2),
                              clientId,
                              RunOverride.Override
                            )
                 )
    } yield sf
      .flatMap(EngineState.sequenceStateIndex[IO](seqObsId1).getOption)
      .exists(_.status.isRunning)).assert
  }
  // test(
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
      List((ObserveClass.Science, StepConfig.Science(Offset.Zero, StepGuideState.Enabled)))
    )

    val startStepIdx  = 1
    val reqConditions = ConstraintSet(
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
      staticCfg1,
      SequenceGen.AtomGen(
        atomId1,
        SequenceType.Science,
        steps = stepList.map { step =>
          SequenceGen.PendingStepGen(
            step.id,
            Monoid.empty[DataId],
            resources = resources,
            _ => InstrumentSystem.Uncontrollable,
            generator = SequenceGen.StepActionsGen(
              odbAction[IO],
              odbAction[IO],
              configs =
                resources.map(r => r -> { (_: SystemOverrides) => pendingAction[IO](r) }).toMap,
              odbAction[IO],
              odbAction[IO],
              post = (_, _) => Nil,
              odbAction[IO],
              odbAction[IO]
            ),
            StepStatusGen.Null,
            step.instrumentConfig,
            step.stepConfig,
            breakpoint = Breakpoint.Disabled
          )
        }.toList
      )
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

  test("ObserveEngine should load new atoms") {
    val atomCount = 2
    val stepCount = 2

    val steps = NonEmptyList(
      Step[DynamicConfig.GmosNorth](
        stepId(1 + stepCount),
        dynamicCfg1,
        stepCfg1,
        StepEstimate.Zero,
        ObserveClass.Science,
        Breakpoint.Disabled
      ),
      List
        .range(2, stepCount + 1)
        .map(i =>
          Step[DynamicConfig.GmosNorth](
            stepId(i + stepCount),
            dynamicCfg1,
            stepCfg1,
            StepEstimate.Zero,
            ObserveClass.Science,
            Breakpoint.Disabled
          )
        )
    )

    for {
      atomIds       <- List
                         .fill(atomCount - 1)(IO.delay(java.util.UUID.randomUUID()))
                         .parSequence
                         .map(_.map(Atom.Id.fromUuid))
      odb           <- TestOdbProxy.build[IO](staticCfg1.some,
                                              List.empty,
                                              atomIds.map(i => Atom[DynamicConfig.GmosNorth](i, none, steps))
                       )
      systems       <- defaultSystems.map(_.copy(odb = odb))
      seqo          <- generateSequence(odbObservation(seqObsId1, stepCount), systems)
      seq           <- seqo.map(_.pure[IO]).getOrElse(IO.delay(fail("Unable to create sequence")))
      s0             = ODBSequencesLoader
                         .loadSequenceEndo[IO](
                           None,
                           seq,
                           EngineState.instrumentLoaded(Instrument.GmosNorth)
                         )
                         .apply(EngineState.default[IO])
      observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings)
      r             <-
        observeEngine
          .start(seqObsId1, user, Observer("Joe".refined), clientId, RunOverride.Override) *>
          observeEngine
            .stream(s0)
            .drop(1)
            .takeThrough(x =>
              x._1 match {
                case EventResult.UserCommandResponse(_, _, Some(SeqEvent.NewAtomLoaded(_))) => false
                case _                                                                      => true
              }
            )
            .compile
            .last
    } yield r
      .flatMap(_._2.sequences.get(seqObsId1))
      .flatMap(_.seqGen.nextAtom.steps.headOption)
      .map(z => assertEquals(z.id, stepId(1 + stepCount)))
      .getOrElse(fail("Bad step id found"))

  }

  test("ObserveEngine start should run the sequence and produce the ODB events") {
    val atomCount = 2
    val stepCount = 2

    def isDatasetStartExposure(ev: TestOdbProxy.OdbEvent): Boolean = ev match {
      case TestOdbProxy.DatasetStartExposure(obsId, _) => obsId === seqObsId1
      case _                                           => false
    }

    def isDatasetEndExposure(ev: TestOdbProxy.OdbEvent): Boolean = ev match {
      case TestOdbProxy.DatasetEndExposure(obsId, _) => obsId === seqObsId1
      case _                                         => false
    }

    def isDatasetStartReadout(ev: TestOdbProxy.OdbEvent): Boolean = ev match {
      case TestOdbProxy.DatasetStartReadout(obsId, _) => obsId === seqObsId1
      case _                                          => false
    }

    def isDatasetEndReadout(ev: TestOdbProxy.OdbEvent): Boolean = ev match {
      case TestOdbProxy.DatasetEndReadout(obsId, _) => obsId === seqObsId1
      case _                                        => false
    }

    def isDatasetStartWrite(ev: TestOdbProxy.OdbEvent): Boolean = ev match {
      case TestOdbProxy.DatasetStartWrite(obsId, _) => obsId === seqObsId1
      case _                                        => false
    }

    def isDatasetEndWrite(ev: TestOdbProxy.OdbEvent): Boolean = ev match {
      case TestOdbProxy.DatasetEndWrite(obsId, _) => obsId === seqObsId1
      case _                                      => false
    }

    def assertAtom(
      l:       List[TestOdbProxy.OdbEvent],
      seqType: SequenceType
    ): List[TestOdbProxy.OdbEvent] = {
      assertEquals(
        List(
          TestOdbProxy.AtomStart(
            seqObsId1,
            Instrument.GmosNorth,
            seqType,
            NonNegShort.unsafeFrom(stepCount.toShort)
          )
        ),
        l.take(1)
      )
      (1 until stepCount).foldLeft(assertStep(l.drop(1))) { case (b, _) =>
        assertStep(b)
      }
    }

    def assertStep(l: List[TestOdbProxy.OdbEvent]): List[TestOdbProxy.OdbEvent] = {
      val chk = List(
        (ev: TestOdbProxy.OdbEvent) =>
          assertEquals(
            ev,
            TestOdbProxy.StepStartStep(seqObsId1, dynamicCfg1, stepCfg1, ObserveClass.Science)
          ),
        (ev: TestOdbProxy.OdbEvent) => assertEquals(ev, TestOdbProxy.StepStartConfigure(seqObsId1)),
        (ev: TestOdbProxy.OdbEvent) => assertEquals(ev, TestOdbProxy.StepEndConfigure(seqObsId1)),
        (ev: TestOdbProxy.OdbEvent) => assertEquals(ev, TestOdbProxy.StepStartObserve(seqObsId1)),
        (ev: TestOdbProxy.OdbEvent) => assert(isDatasetStartExposure(ev)),
        (ev: TestOdbProxy.OdbEvent) => assert(isDatasetEndExposure(ev)),
        (ev: TestOdbProxy.OdbEvent) => assert(isDatasetStartReadout(ev)),
        (ev: TestOdbProxy.OdbEvent) => assert(isDatasetEndReadout(ev)),
        (ev: TestOdbProxy.OdbEvent) => assert(isDatasetStartWrite(ev)),
        (ev: TestOdbProxy.OdbEvent) => assert(isDatasetEndWrite(ev)),
        (ev: TestOdbProxy.OdbEvent) => assertEquals(ev, TestOdbProxy.StepEndObserve(seqObsId1)),
        (ev: TestOdbProxy.OdbEvent) => assertEquals(ev, TestOdbProxy.StepEndStep(seqObsId1))
      )

      assert(l.length >= chk.length)
      chk.zip(l).foreach { case (f, x) => f(x) }
      l.drop(chk.length)
    }

    val firstEvents = List(
      TestOdbProxy.VisitStart(seqObsId1, staticCfg1),
      TestOdbProxy.SequenceStart(seqObsId1)
    )

    val steps = NonEmptyList(
      Step[DynamicConfig.GmosNorth](
        stepId(1),
        dynamicCfg1,
        stepCfg1,
        StepEstimate.Zero,
        ObserveClass.Science,
        Breakpoint.Disabled
      ),
      List
        .range(2, stepCount + 1)
        .map(i =>
          Step[DynamicConfig.GmosNorth](
            stepId(i),
            dynamicCfg1,
            stepCfg1,
            StepEstimate.Zero,
            ObserveClass.Science,
            Breakpoint.Disabled
          )
        )
    )

    for {
      atomIds       <- List
                         .fill(atomCount - 1)(IO.delay(java.util.UUID.randomUUID()))
                         .parSequence
                         .map(_.map(Atom.Id.fromUuid))
      odb           <- TestOdbProxy.build[IO](staticCfg1.some,
                                              List.empty,
                                              atomIds.map(i => Atom[DynamicConfig.GmosNorth](i, none, steps))
                       )
      systems       <- defaultSystems.map(_.copy(odb = odb))
      seqo          <- generateSequence(odbObservation(seqObsId1, stepCount), systems)
      seq           <- seqo.map(_.pure[IO]).getOrElse(IO.delay(fail("Unable to create sequence")))
      s0             = ODBSequencesLoader
                         .loadSequenceEndo[IO](
                           None,
                           seq,
                           EngineState.instrumentLoaded(Instrument.GmosNorth)
                         )
                         .apply(EngineState.default[IO])
      observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings)
      _             <- observeEngine
                         .start(seqObsId1, user, Observer("Joe".refined), clientId, RunOverride.Override) *>
                         observeEngine
                           .stream(s0)
                           .drop(1)
                           .takeThrough(_._2.sequences.get(seqObsId1).exists(x => !isFinished(x.seq.status)))
                           .compile
                           .last
      res           <- odb.outCapture
    } yield {
      assertEquals(res.take(firstEvents.length), firstEvents)
      (1 until atomCount).foldLeft(
        assertAtom(res.drop(firstEvents.length), SequenceType.Acquisition)
      ) { case (b, _) =>
        assertAtom(b, SequenceType.Science)
      }
    }
  }

}
