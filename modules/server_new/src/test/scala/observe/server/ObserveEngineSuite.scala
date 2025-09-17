// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Monoid
import cats.data.NonEmptyList
import cats.effect.Async
import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all.*
import coulomb.integrations.cats.all.given
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.numeric.PosLong
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.enums.*
import lucuma.core.model.CloudExtinction
import lucuma.core.model.ConstraintSet
import lucuma.core.model.ElevationRange
import lucuma.core.model.ImageQuality
import lucuma.core.model.Program
import lucuma.core.model.Target
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Step
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.StepEstimate
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.refined.auto.*
import lucuma.core.refined.given
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation as ODBObservation
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment.FirstScienceTarget
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment.GuideEnvironment
import observe.common.test.*
import observe.model
import observe.model.ClientId
import observe.model.Conditions
import observe.model.Observer
import observe.model.Operator
import observe.model.SequenceState
import observe.model.SequenceState.*
import observe.model.SystemOverrides
import observe.model.UserPrompt
import observe.model.dhs.DataId
import observe.model.enums.Resource
import observe.model.enums.Resource.Gcal
import observe.model.enums.Resource.TCS
import observe.model.enums.RunOverride
import observe.server.SeqEvent.RequestConfirmation
import observe.server.SequenceGen.StepStatusGen
import observe.server.engine.Breakpoints
import observe.server.engine.EventResult
import observe.server.engine.EventResult.Outcome
import observe.server.engine.EventResult.SystemUpdate
import observe.server.engine.Sequence
import observe.server.engine.SystemEvent
import observe.server.engine.user
import observe.server.odb.TestOdbProxy
import observe.server.odb.TestOdbProxy.StepStartStep
import observe.server.tcs.DummyTargetKeywordsReader
import observe.server.tcs.DummyTcsKeywordsReader
import observe.server.tcs.TargetKeywordsReader

import java.util.UUID

class ObserveEngineSuite extends TestCommon {

  import TestCommon.*

  private val clientId: model.ClientId.Type = ClientId(UUID.randomUUID())
  private val observer: Observer            = Observer("Joe".refined)
  private val operator: Operator            = Operator("Joe".refined)

  private case class EngineObserver[F[_]: Async](
    oe:           ObserveEngine[F],
    initialState: EngineState[F] = EngineState.default[F]
  ):
    private val state: Ref[F, EngineState[F]] = Ref.unsafe(initialState)

    def executeAndWait(
      f:     ObserveEngine[F] => F[Unit],
      until: PartialFunction[(EventResult, EngineState[F]), Boolean]
    ): F[EngineState[F]] =
      for
        _ <- f(oe)
        s <- state.get
        r <- oe.stream(s)
               //  .evalTap(r => cats.effect.Sync[F].delay(println(s"**** ${r._1}"))) // Uncomment for debugging
               .takeThrough(r => !until.lift(r).getOrElse(false))
               .compile
               .last
        s1 = r.get._2
        _ <- state.set(s1)
      yield s1

    def executeAndWaitResult(
      f:     ObserveEngine[F] => F[Unit],
      until: PartialFunction[EventResult, Boolean]
    ): F[EngineState[F]] =
      executeAndWait(f, r => until.lift(r._1).getOrElse(false))

    def executeAndWaitState(
      f:     ObserveEngine[F] => F[Unit],
      until: PartialFunction[EngineState[F], Boolean]
    ): F[EngineState[F]] =
      executeAndWait(f, r => until.lift(r._2).getOrElse(false))

  test("ObserveEngine setOperator should set operator's name") {
    val s0 = EngineState.default[IO]
    (for {
      oe <- observeEngine
      sf <- advanceN(oe, s0, oe.setOperator(user, operator), 2)
    } yield sf.flatMap(EngineState.operator.get).exists { op =>
      op === operator
    }).assert
  }

  test("ObserveEngine setImageQuality should set Image Quality condition") {
    val iq = ImageQuality.Preset.PointEight.toImageQuality
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
    val ce = CloudExtinction.Preset.TwoPointZero.toCloudExtinction
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
    val s0 = ODBSequencesLoader
      .loadSequenceEndo[IO](
        None,
        sequence(seqObsId1),
        EngineState.instrumentLoaded(Instrument.GmosNorth),
        IO.unit
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
        sequenceWithResources(seqObsId1, Set(Instrument.GmosNorth, TCS)),
        EngineState.instrumentLoaded(Instrument.GmosNorth),
        IO.unit
      ) >>>
        ODBSequencesLoader.loadSequenceEndo[IO](
          None,
          sequenceWithResources(seqObsId2, Set(Instrument.GmosSouth, TCS)),
          EngineState.instrumentLoaded(Instrument.GmosSouth),
          IO.unit
        ) >>>
        EngineState
          .sequenceStateAt[IO](seqObsId1)
          .andThen(Sequence.State.status[IO])
          .replace(SequenceState.Running.Init)
    ).apply(EngineState.default[IO])

    (for {
      oe <- observeEngine
      sf <- advanceOne(
              oe,
              s0,
              oe.start(seqObsId2, user, observer, clientId, RunOverride.Default)
            )
    } yield sf
      .flatMap(EngineState.sequenceStateAt[IO](seqObsId2).getOption)
      .exists(_.status.isIdle)).assert
  }

  test("ObserveEngine should run 2nd sequence when there are no shared resources") {
    val s0 = (
      ODBSequencesLoader.loadSequenceEndo[IO](
        None,
        sequenceWithResources(seqObsId1, Set(Instrument.GmosNorth, TCS)),
        EngineState.instrumentLoaded(Instrument.GmosNorth),
        IO.unit
      ) >>>
        ODBSequencesLoader.loadSequenceEndo[IO](
          None,
          sequenceWithResources(seqObsId2, Set(Instrument.GmosSouth)),
          EngineState.instrumentLoaded(Instrument.GmosSouth),
          IO.unit
        ) >>>
        EngineState
          .sequenceStateAt[IO](seqObsId1)
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
                observer,
                clientId,
                RunOverride.Default
              ),
              2
            )
    } yield sf
      .flatMap(EngineState.sequenceStateAt[IO](seqObsId2).getOption)
      .exists(_.status.isRunning)).assert
  }

  test("ObserveEngine configSystem should run a system configuration") {
    val s0 = ODBSequencesLoader
      .loadSequenceEndo[IO](
        None,
        sequenceWithResources(seqObsId1, Set(Instrument.GmosNorth, TCS)),
        EngineState.instrumentLoaded(Instrument.GmosNorth),
        IO.unit
      )
      .apply(EngineState.default[IO])

    (for {
      oe <- observeEngine
      sf <- advanceN(
              oe,
              s0,
              oe.configSystem(
                seqObsId1,
                observer,
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
      sequenceWithResources(seqObsId1, Set(Instrument.GmosNorth, TCS)),
      EngineState.instrumentLoaded(Instrument.GmosNorth),
      IO.unit
    ) >>>
      EngineState
        .sequenceStateAt[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.Init)).apply(EngineState.default[IO])

    (for {
      oe <- observeEngine
      sf <- advanceOne(
              oe,
              s0,
              oe.configSystem(
                seqObsId1,
                observer,
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
      sequenceWithResources(seqObsId1, Set(Instrument.GmosNorth, TCS)),
      EngineState.instrumentLoaded(Instrument.GmosNorth),
      IO.unit
    ) >>>
      ODBSequencesLoader.loadSequenceEndo[IO](
        None,
        sequenceWithResources(seqObsId2, Set(Instrument.GmosSouth, TCS)),
        EngineState.instrumentLoaded(Instrument.GmosSouth),
        IO.unit
      ) >>>
      EngineState
        .sequenceStateAt[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.Init)).apply(EngineState.default[IO])

    (for {
      oe <- observeEngine
      sf <-
        advanceOne(
          oe,
          s0,
          oe.configSystem(seqObsId2, observer, user, stepId(1), TCS, clientId)
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
      sequenceWithResources(seqObsId1, Set(Instrument.GmosNorth, TCS)),
      EngineState.instrumentLoaded(Instrument.GmosNorth),
      IO.unit
    ) >>>
      ODBSequencesLoader.loadSequenceEndo[IO](
        None,
        sequenceWithResources(seqObsId2, Set(Instrument.GmosNorth, Gcal)),
        EngineState.instrumentLoaded(Instrument.GmosSouth),
        IO.unit
      ) >>>
      EngineState
        .sequenceStateAt[IO](seqObsId1)
        .andThen(Sequence.State.status[IO])
        .replace(SequenceState.Running.Init)).apply(EngineState.default[IO])

    (for {
      oe <- observeEngine
      sf <- advanceN(
              oe,
              s0,
              oe
                .configSystem(seqObsId2, observer, user, stepId(1), Gcal, clientId),
              3
            )
    } yield sf
      .flatMap(EngineState.atSequence(seqObsId2).getOption)
      .flatMap(s => s.seqGen.configActionCoord(stepId(1), Gcal).map(s.seq.getSingleState))
      .exists(_.started)).assert
  }

  private def testTargetSequence(targetName: NonEmptyString): SequenceGen[IO] = {
    val resources: Set[Resource | Instrument] = Set(Instrument.GmosNorth, TCS)

    val obsTypes: NonEmptyList[(ObserveClass, StepConfig)] = NonEmptyList(
      (ObserveClass.NightCal, StepConfig.Dark),
      List((ObserveClass.Science, StepConfig.Science))
    )

    val startStepIdx  = 1
    val reqConditions = ConstraintSet(
      ImageQuality.Preset.PointTwo,
      CloudExtinction.Preset.PointFive,
      SkyBackground.Dark,
      WaterVapor.Median,
      ElevationRange.ByHourAngle.Default
    )

    val stepList: NonEmptyList[Step[DynamicConfig.GmosNorth]] = obsTypes.zipWithIndex.map {
      case ((cl, st), idx) =>
        Step[DynamicConfig.GmosNorth](
          stepId(idx + startStepIdx),
          dynamicCfg1,
          st,
          telescopeCfg1,
          StepEstimate.Zero,
          cl,
          Breakpoint.Disabled
        )
    }

    SequenceGen.GmosNorth[IO](
      ODBObservation(
        id = seqObsId1,
        title = "Test Observation".refined,
        ODBObservation.Program(
          Program.Id(PosLong.unsafeFrom(123)),
          None,
          ODBObservation.Program.Goa(NonNegInt.unsafeFrom(0))
        ),
        TargetEnvironment(
          Some(FirstScienceTarget(Target.Id.fromLong(1).get, targetName)),
          GuideEnvironment(List.empty)
        ),
        reqConditions,
        List.empty,
        ODBObservation.Itc(
          ODBObservation.Itc.Acquisition(ODBObservation.Itc.Acquisition.Selected(none)),
          ODBObservation.Itc.Science(ODBObservation.Itc.Science.Selected(none))
        )
      ),
      staticCfg1,
      SequenceGen.AtomGen.GmosNorth(
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
            step.telescopeConfig,
            signalToNoise = none,
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
        EngineState.instrumentLoaded(Instrument.GmosNorth),
        IO.unit
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
                              observer,
                              clientId,
                              RunOverride.Default
                            )
                 )
    } yield sf
      .flatMap(EngineState.sequenceStateAt[IO](seqObsId1).getOption)
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
        EngineState.instrumentLoaded(Instrument.GmosNorth),
        IO.unit
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
                              observer,
                              clientId,
                              RunOverride.Default
                            )
                 )
    } yield sf
      .flatMap(EngineState.sequenceStateAt[IO](seqObsId1).getOption)
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
        EngineState.instrumentLoaded(Instrument.GmosNorth),
        IO.unit
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
                              observer,
                              clientId,
                              RunOverride.Override
                            )
                 )
    } yield sf
      .flatMap(EngineState.sequenceStateAt[IO](seqObsId1).getOption)
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
//       sf.flatMap(EngineState.sequenceStateAt[IO](seqObsId1).getOption).map(_.status)
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
//       sf.flatMap(EngineState.sequenceStateAt[IO](seqObsId1).getOption).map(_.status)
//     ) { case Some(status) =>
//       assert(status.isRunning)
//     }).unsafeRunSync()
//   }

  private val testConditionsSequence: SequenceGen[IO] = {
    val resources: Set[Resource | Instrument] = Set(Instrument.GmosNorth, TCS)

    val obsTypes: NonEmptyList[(ObserveClass, StepConfig)] = NonEmptyList(
      (ObserveClass.NightCal, StepConfig.Dark),
      List((ObserveClass.Science, StepConfig.Science))
    )

    val startStepIdx  = 1
    val reqConditions = ConstraintSet(
      ImageQuality.Preset.PointTwo,
      CloudExtinction.Preset.PointFive,
      SkyBackground.Dark,
      WaterVapor.Median,
      ElevationRange.ByHourAngle.Default
    )

    val stepList: NonEmptyList[Step[DynamicConfig.GmosNorth]] = obsTypes.zipWithIndex.map {
      case ((cl, st), idx) =>
        Step[DynamicConfig.GmosNorth](
          stepId(idx + startStepIdx),
          dynamicCfg1,
          st,
          telescopeCfg1,
          StepEstimate.Zero,
          cl,
          Breakpoint.Disabled
        )
    }

    SequenceGen.GmosNorth[IO](
      ODBObservation(
        id = seqObsId1,
        title = "Test Observation".refined,
        ODBObservation.Program(
          Program.Id(PosLong.unsafeFrom(123)),
          None,
          ODBObservation.Program.Goa(NonNegInt.unsafeFrom(0))
        ),
        TargetEnvironment(None, GuideEnvironment(List.empty)),
        reqConditions,
        List.empty,
        ODBObservation.Itc(
          ODBObservation.Itc.Acquisition(ODBObservation.Itc.Acquisition.Selected(none)),
          ODBObservation.Itc.Science(ODBObservation.Itc.Science.Selected(none))
        )
      ),
      staticCfg1,
      SequenceGen.AtomGen.GmosNorth(
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
            step.telescopeConfig,
            signalToNoise = none,
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
      EngineState.instrumentLoaded(Instrument.GmosNorth),
      IO.unit
    ) >>>
      EngineState.conditions
        .andThen(Conditions.iq)
        .replace(ImageQuality.Preset.PointTwo.toImageQuality.some) >>>
      EngineState.conditions.andThen(Conditions.wv).replace(WaterVapor.Median.some) >>>
      EngineState.conditions.andThen(Conditions.sb).replace(SkyBackground.Dark.some) >>>
      EngineState.conditions
        .andThen(Conditions.ce)
        .replace(CloudExtinction.Preset.PointFive.toCloudExtinction.some))
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
                           observer,
                           clientId,
                           RunOverride.Default
                         )
                       )
    } yield sf.flatMap(EngineState.sequenceStateAt[IO](seqObsId1).getOption)).map { s =>
      assert(s.exists(_.status.isRunning))
      assert(s.flatMap(_.currentStep).exists(_.id === stepId(1)))
    }
  }

  test("ObserveEngine start should not start the sequence if it fails the conditions check") {
    val seq = testConditionsSequence

    val s0 = (ODBSequencesLoader.loadSequenceEndo[IO](
      None,
      seq,
      EngineState.instrumentLoaded(Instrument.GmosNorth),
      IO.unit
    ) >>>
      EngineState.conditions
        .andThen(Conditions.iq)
        .replace(ImageQuality.Preset.OnePointZero.toImageQuality.some) >>>
      EngineState.conditions.andThen(Conditions.wv).replace(WaterVapor.Dry.some) >>>
      EngineState.conditions.andThen(Conditions.sb).replace(SkyBackground.Darkest.some) >>>
      EngineState.conditions
        .andThen(Conditions.ce)
        .replace(CloudExtinction.Preset.OnePointZero.toCloudExtinction.some))
      .apply(EngineState.default[IO])

    for {
      systems       <- defaultSystems
      observeEngine <- ObserveEngine.build(Site.GN, systems, defaultSettings)
      result        <-
        observeEngine.start(
          seqObsId1,
          user,
          observer,
          clientId,
          RunOverride.Default
        ) *>
          observeEngine.stream(s0).take(1).compile.last
    } yield result
      .map { case (out, sf) =>
        assert(EngineState.sequenceStateAt[IO](seqObsId1).getOption(sf).exists(_.status.isIdle))
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
      EngineState.instrumentLoaded(Instrument.GmosNorth),
      IO.unit
    ) >>>
      EngineState.conditions
        .andThen(Conditions.iq)
        .replace(ImageQuality.Preset.OnePointZero.toImageQuality.some) >>>
      EngineState.conditions.andThen(Conditions.wv).replace(WaterVapor.Dry.some) >>>
      EngineState.conditions.andThen(Conditions.sb).replace(SkyBackground.Darkest.some) >>>
      EngineState.conditions
        .andThen(Conditions.ce)
        .replace(CloudExtinction.Preset.OnePointZero.toCloudExtinction.some))
      .apply(EngineState.default[IO])

    for {
      systems       <- defaultSystems
      observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings)
      sf            <-
        advanceN(
          observeEngine,
          s0,
          observeEngine
            .start(seqObsId1, user, observer, clientId, RunOverride.Override),
          3
        )
    } yield {
      assert(
        sf.flatMap(EngineState.sequenceStateAt[IO](seqObsId1).getOption)
          .exists(_.status.isRunning)
      )
      assert(
        sf.flatMap(EngineState.sequenceStateAt[IO](seqObsId1).getOption)
          .flatMap(_.currentStep)
          .exists(_.id === stepId(1))
      )
    }
  }

  test("ObserveEngine loadNextAtom should load the next atom and restart the execution") {
    val acquisitionStepCount = 2
    val scienceAtomCount     = 2
    val scienceStepCount     = 2
    val firstScienceStepId   = acquisitionStepCount * 2 + 1

    val acquisitionSteps = NonEmptyList(
      Step[DynamicConfig.GmosNorth](
        stepId(1),
        dynamicCfg1,
        stepCfg1,
        telescopeCfg1,
        StepEstimate.Zero,
        ObserveClass.Science,
        Breakpoint.Disabled
      ),
      List
        .range(2, acquisitionStepCount + 1)
        .map(i =>
          Step[DynamicConfig.GmosNorth](
            stepId(i),
            dynamicCfg1,
            stepCfg1,
            telescopeCfg1,
            StepEstimate.Zero,
            ObserveClass.Science,
            Breakpoint.Disabled
          )
        )
    )
    val scienceSteps     = NonEmptyList(
      Step[DynamicConfig.GmosNorth](
        stepId(firstScienceStepId),
        dynamicCfg1,
        stepCfg1,
        telescopeCfg1,
        StepEstimate.Zero,
        ObserveClass.Science,
        Breakpoint.Disabled
      ),
      List
        .range(firstScienceStepId + 1, firstScienceStepId + scienceStepCount)
        .map(i =>
          Step[DynamicConfig.GmosNorth](
            stepId(i),
            dynamicCfg1,
            stepCfg1,
            telescopeCfg1,
            StepEstimate.Zero,
            ObserveClass.Science,
            Breakpoint.Disabled
          )
        )
    )

    for {
      acqAtomId     <- IO.delay(java.util.UUID.randomUUID()).map(Atom.Id.fromUuid)
      atomIds       <- List
                         .fill(scienceAtomCount)(IO.delay(java.util.UUID.randomUUID()))
                         .parSequence
                         .map(_.map(Atom.Id.fromUuid))
      odb           <- TestOdbProxy.build[IO](
                         staticCfg1,
                         Atom[DynamicConfig.GmosNorth](acqAtomId, none, acquisitionSteps).some,
                         atomIds.map(i => Atom[DynamicConfig.GmosNorth](i, none, scienceSteps))
                       )
      systems       <- defaultSystems.map(_.copy(odb = odb))
      oseq          <- odb.read(seqObsId1)
      seqo          <- generateSequence(oseq, systems)
      seq           <- seqo.map(_.pure[IO]).getOrElse(IO.delay(fail("Unable to create sequence")))
      s0             = ODBSequencesLoader
                         .loadSequenceEndo[IO](
                           None,
                           seq,
                           EngineState.instrumentLoaded(Instrument.GmosNorth),
                           IO.unit
                         )
                         .apply(EngineState.default[IO])
      s1             =
        EngineState
          .sequenceStateAt[IO](seqObsId1)
          .modify(x =>
            Sequence.State.Final(
              x.toSequence,
              Running(
                HasUserStop.No,
                HasInternalStop.No,
                IsWaitingUserPrompt.No,
                IsWaitingNextAtom.Yes,
                IsStarting.No
              ),
              Breakpoints.empty
            )
          )(s0)
      observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings)
      eo             = EngineObserver(observeEngine, s1)
      r             <-
        eo.executeAndWaitResult(
          _.loadNextAtom(seqObsId1, user, observer, SequenceType.Acquisition),
          {
            case EventResult.UserCommandResponse(
                  _,
                  _,
                  Some(SeqEvent.AtomCompleted(seqObsId1, SequenceType.Acquisition, acqAtomId))
                ) =>
              true
          }
        )
      s             <-
        eo.executeAndWaitResult(
          _.loadNextAtom(seqObsId1, user, observer, SequenceType.Science),
          {
            case EventResult.UserCommandResponse(
                  _,
                  _,
                  Some(SeqEvent.AtomCompleted(seqObsId1, SequenceType.Science, acqAtomId))
                ) =>
              true
          }
        )
    } yield {
      r.sequences
        .get(seqObsId1)
        .flatMap(_.seqGen.nextAtom.steps.headOption)
        .map(z => assertEquals(z.id, acquisitionSteps.get(1).get.id))
        .getOrElse(fail("Bad step id found"))
      s.sequences
        .get(seqObsId1)
        .flatMap(_.seqGen.nextAtom.steps.headOption)
        .map(z => assertEquals(z.id, scienceSteps.get(1).get.id))
        .getOrElse(fail("Bad step id found"))
    }
  }

  test("ObserveEngine should automatically load new science atoms") {
    val atomCount = 2
    val stepCount = 2

    val steps = NonEmptyList(
      Step[DynamicConfig.GmosNorth](
        stepId(1 + stepCount),
        dynamicCfg1,
        stepCfg1,
        telescopeCfg1,
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
            telescopeCfg1,
            StepEstimate.Zero,
            ObserveClass.Science,
            Breakpoint.Disabled
          )
        )
    )

    for {
      atomIds       <- List
                         .fill(atomCount)(IO.randomUUID)
                         .parSequence
                         .map(_.map(Atom.Id.fromUuid))
      odb           <- TestOdbProxy.build[IO](
                         staticCfg1,
                         none,
                         atomIds.map(i => Atom[DynamicConfig.GmosNorth](i, none, steps))
                       )
      systems       <- defaultSystems.map(_.copy(odb = odb))
      oseq          <- odb.read(seqObsId1)
      seqo          <- generateSequence(oseq, systems)
      seq           <- seqo.map(_.pure[IO]).getOrElse(IO.delay(fail("Unable to create sequence")))
      s0             = ODBSequencesLoader
                         .loadSequenceEndo[IO](
                           None,
                           seq,
                           EngineState.instrumentLoaded(Instrument.GmosNorth),
                           IO.unit
                         )
                         .apply(EngineState.default[IO])
      observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings)
      eo             = EngineObserver(observeEngine, s0)
      r             <-
        eo.executeAndWaitResult(
          _.start(seqObsId1, user, observer, clientId, RunOverride.Override),
          { case EventResult.UserCommandResponse(_, _, Some(SeqEvent.AtomCompleted(_, _, _))) =>
            true
          }
        )
    } yield r.sequences
      .get(seqObsId1)
      .flatMap(_.seqGen.nextAtom.steps.headOption)
      .map(z => assertEquals(z.id, stepId(2 + stepCount)))
      .getOrElse(fail("Bad step id found"))

  }

  def assertStep(l: List[TestOdbProxy.OdbEvent]): List[TestOdbProxy.OdbEvent] = {
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

    val chk = List(
      (ev: TestOdbProxy.OdbEvent) =>
        assertEquals(
          ev,
          TestOdbProxy.StepStartStep(
            seqObsId1,
            dynamicCfg1,
            stepCfg1,
            telescopeCfg1,
            ObserveClass.Science
          )
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

  def assertAtom(
    l:             List[TestOdbProxy.OdbEvent],
    atomStepCount: Int
  ): List[TestOdbProxy.OdbEvent] = {
    val ss = (1 until atomStepCount).foldLeft(assertStep(l)) { case (b, _) =>
      assertStep(b)
    }

    ss
  }

  test("ObserveEngine start should run the sequence and produce the ODB events") {
    val atomCount = 2
    val stepCount = 2

    val firstEvents = List(
      TestOdbProxy.VisitStart(seqObsId1, staticCfg1),
      TestOdbProxy.SequenceStart(seqObsId1)
    )

    def steps(k: Int) = NonEmptyList(
      Step[DynamicConfig.GmosNorth](
        stepId(1 + k),
        dynamicCfg1,
        stepCfg1,
        telescopeCfg1,
        StepEstimate.Zero,
        ObserveClass.Science,
        Breakpoint.Disabled
      ),
      List
        .range(2, stepCount + 1)
        .map(i =>
          Step[DynamicConfig.GmosNorth](
            stepId(i + k),
            dynamicCfg1,
            stepCfg1,
            telescopeCfg1,
            StepEstimate.Zero,
            ObserveClass.Science,
            Breakpoint.Disabled
          )
        )
    )

    for {
      atomIds       <- List
                         .fill(atomCount)(IO.delay(java.util.UUID.randomUUID()))
                         .parSequence
                         .map(_.map(Atom.Id.fromUuid))
      odb           <-
        TestOdbProxy.build[IO](
          staticCfg1,
          none,
          atomIds.zipWithIndex.map((i, k) => Atom[DynamicConfig.GmosNorth](i, none, steps(2 * k)))
        )
      systems       <- defaultSystems.map(_.copy(odb = odb))
      oseq          <- odb.read(seqObsId1)
      seqo          <- generateSequence(oseq, systems)
      seq           <- seqo.map(_.pure[IO]).getOrElse(IO.delay(fail("Unable to create sequence")))
      s0             = ODBSequencesLoader
                         .loadSequenceEndo[IO](
                           None,
                           seq,
                           EngineState.instrumentLoaded(Instrument.GmosNorth),
                           IO.unit
                         )
                         .apply(EngineState.default[IO])
      observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings)
      eo             = EngineObserver(observeEngine, s0)
      _             <- eo.executeAndWaitState(
                         _.start(seqObsId1, user, observer, clientId, RunOverride.Override),
                         _.sequences.get(seqObsId1).forall(x => isFinished(x.seq.status))
                       )
      res           <- odb.outCapture
    } yield {
      assertEquals(res.take(firstEvents.length), firstEvents)
      val rest = (1 until atomCount).foldLeft(
        assertAtom(res.drop(firstEvents.length), stepCount)
      ) { case (b, _) =>
        assertAtom(b, stepCount)
      }
      assertEquals(rest.length, 0)
    }
  }

  test(
    "ObserveEngine should run the sequence and load new events, executing the last data provided by the ODB"
  ) {
    val atomCount = 2
    val stepCount = 2

    val firstEvents = List(
      TestOdbProxy.VisitStart(seqObsId1, staticCfg1),
      TestOdbProxy.SequenceStart(seqObsId1)
    )

    def steps(k: Int) = NonEmptyList(
      Step[DynamicConfig.GmosNorth](
        stepId(1 + k),
        dynamicCfg1,
        stepCfg1,
        telescopeCfg1,
        StepEstimate.Zero,
        ObserveClass.Science,
        Breakpoint.Disabled
      ),
      List
        .range(2, stepCount + 1)
        .map(i =>
          Step[DynamicConfig.GmosNorth](
            stepId(i + k),
            dynamicCfg1,
            stepCfg1,
            telescopeCfg1,
            StepEstimate.Zero,
            ObserveClass.Science,
            Breakpoint.Disabled
          )
        )
    )

    // Ugly flag but it would be quite complicated to avoid it
    var addStep = true
    for {
      atomIds       <- List
                         .fill(atomCount)(IO.delay(java.util.UUID.randomUUID()))
                         .parSequence
                         .map(_.map(Atom.Id.fromUuid))
      odb           <-
        TestOdbProxy.build[IO](
          staticCfg1,
          none,
          atomIds.zipWithIndex.map((i, k) => Atom[DynamicConfig.GmosNorth](i, none, steps(2 * k))),
          s =>
            if (addStep) {
              // Only once we add an extra setp at the beggining of the atom
              addStep = false
              s.copy(sciences =
                s.sciences.map(s =>
                  if (atomIds.headOption.exists(_ === s.id))
                    s.copy(steps = steps(5).head :: s.steps)
                  else s
                )
              )
            } else s
        )
      systems       <- defaultSystems.map(_.copy(odb = odb))
      oseq          <- odb.read(seqObsId1)
      seqo          <- generateSequence(oseq, systems)
      seq           <- seqo.map(_.pure[IO]).getOrElse(IO.delay(fail("Unable to create sequence")))
      s0             = ODBSequencesLoader
                         .loadSequenceEndo[IO](
                           None,
                           seq,
                           EngineState.instrumentLoaded(Instrument.GmosNorth),
                           IO.unit
                         )
                         .apply(EngineState.default[IO])
      observeEngine <- ObserveEngine.build(Site.GS, systems, defaultSettings)
      eo             = EngineObserver(observeEngine, s0)
      _             <- eo.executeAndWaitState(
                         _.start(seqObsId1, user, observer, clientId, RunOverride.Override),
                         _.sequences.get(seqObsId1).forall(x => isFinished(x.seq.status))
                       )
      res           <- odb.outCapture
    } yield {
      assertEquals(res.take(firstEvents.length), firstEvents)
      val rest = (1 until atomCount).foldLeft {
        assertAtom(res.drop(firstEvents.length), 3)
      } { case (b, _) =>
        assertAtom(b, 2)
      }
      assertEquals(rest.length, 0)
    }
  }

  test("ObserveEngine should reset acquisition sequence if reloaded") {
    val stepCount = 2

    val steps = NonEmptyList(
      Step[DynamicConfig.GmosNorth](
        stepId(1 + stepCount),
        dynamicCfg1,
        stepCfg1,
        telescopeCfg1,
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
            telescopeCfg1,
            StepEstimate.Zero,
            ObserveClass.Science,
            Breakpoint.Enabled
          )
        )
    )

    for
      atomId        <- IO.randomUUID.map(Atom.Id.fromUuid)
      odb           <- TestOdbProxy.build[IO](
                         staticCfg1,
                         Atom[DynamicConfig.GmosNorth](atomId, none, steps).some,
                         List.empty
                       )
      systems       <- defaultSystems.map(_.copy(odb = odb))
      observeEngine <- ObserveEngine.build(Site.GN, systems, defaultSettings)
      eo             = EngineObserver(observeEngine)
      s1            <-
        eo.executeAndWaitResult(
          _.selectSequence(Instrument.GmosNorth, seqObsId1, observer, user, clientId),
          { case EventResult.UserCommandResponse(_, _, Some(SeqEvent.LoadSequence(_))) => true }
        )
      _              = // Check all steps loaded
        assertEquals(s1.sequences.get(seqObsId1).get(0).get.seqGen.nextAtom.steps.length, stepCount)
      _              = // Check no steps were executed
        assertEquals(s1.sequences.get(seqObsId1).get(0).get.seq.done.length, 0)
      r             <-
        eo.executeAndWaitResult(
          _.start(seqObsId1, user, observer, clientId, RunOverride.Override),
          { case EventResult.SystemUpdate(SystemEvent.BreakpointReached(_), _) => true }
        )
      _              = // Check one step was executed
        assertEquals(r.sequences.get(seqObsId1).get(0).get.seq.done.length, 1)
      s1            <-
        eo.executeAndWaitResult(
          _.selectSequence(Instrument.GmosNorth, seqObsId1, observer, user, clientId),
          { case EventResult.UserCommandResponse(_, _, Some(SeqEvent.LoadSequence(_))) => true }
        )
    yield
      // Check all steps loaded
      assertEquals(s1.sequences.get(seqObsId1).get(0).get.seqGen.nextAtom.steps.length, stepCount)
      // Check no steps were executed
      assertEquals(s1.sequences.get(seqObsId1).get(0).get.seq.done.length, 0)
  }
}
