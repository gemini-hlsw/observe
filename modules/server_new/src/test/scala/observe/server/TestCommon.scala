// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.effect.IO
import cats.syntax.all.*
import cats.{Applicative, Monoid}
import fs2.Stream
import eu.timepit.refined.types.numeric.PosLong
import giapi.client.ghost.GhostClient
import giapi.client.gpi.GpiClient
import lucuma.core.enums.{
  Breakpoint,
  CloudExtinction,
  GmosAmpCount,
  GmosAmpGain,
  GmosAmpReadMode,
  GmosDtax,
  GmosGratingOrder,
  GmosNorthDetector,
  GmosNorthFpu,
  GmosNorthGrating,
  GmosNorthStageMode,
  GmosRoi,
  GmosXBinning,
  GmosYBinning,
  GuideState,
  ImageQuality,
  MosPreImaging,
  ObsActiveStatus,
  ObsStatus,
  ObserveClass,
  Site,
  SkyBackground,
  WaterVapor
}
import lucuma.core.math.{Offset, Wavelength}
import lucuma.core.model.sequence.InstrumentExecutionConfig.GmosNorth
import lucuma.core.model.sequence.gmos.{
  DynamicConfig,
  GmosCcdMode,
  GmosFpuMask,
  GmosGratingConfig,
  StaticConfig
}
import lucuma.core.model.{ConstraintSet, ElevationRange, Program}
import lucuma.core.model.sequence.{
  Atom,
  ExecutionConfig,
  ExecutionSequence,
  Step,
  StepConfig,
  StepEstimate
}
import lucuma.core.util.TimeSpan
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.Execution
import observe.common.test.*
import observe.engine
import observe.engine.Result.{PartialVal, PauseContext}
import observe.engine.{Action, Result}
import observe.model.config.*
import observe.model.dhs.*
import observe.model.enums.{Instrument, Resource}
import observe.model.{ActionType, ClientId, Observation, SystemOverrides}
import observe.server.OdbProxy.TestOdbProxy
import observe.server.SequenceGen.StepStatusGen
import observe.server.altair.{AltairControllerSim, AltairKeywordReaderDummy}
// import observe.server.flamingos2.Flamingos2ControllerSim
// import observe.server.gnirs.{GnirsControllerSim, GnirsKeywordReaderDummy}
// import observe.server.gpi.GpiController
// import observe.server.gws.DummyGwsKeywordsReader
// import observe.server.nifs.{NifsControllerSim, NifsKeywordReaderDummy}
// import observe.server.niri.{NiriControllerSim, NiriKeywordReaderDummy}
import observe.server.tcs.*
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation as ODBObservation
import org.http4s.Uri
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import scala.concurrent.duration.*
import observe.model.enums.Resource
import observe.server.keywords.DhsClientProvider
import observe.server.keywords.DhsClient

trait TestCommon extends munit.CatsEffectSuite {
  import TestCommon.*

  val defaultSystems: IO[Systems[IO]] = Systems.dummy[IO]

  val observeEngine: IO[ObserveEngine[IO]] =
    defaultSystems.flatMap(ObserveEngine.build(Site.GS, _, defaultSettings))

  def advanceOne(
    oe:  ObserveEngine[IO],
    s0:  EngineState[IO],
    put: IO[Unit]
  ): IO[Option[EngineState[IO]]] =
    advanceN(oe, s0, put, 1L)

  def advanceN(
    oe:  ObserveEngine[IO],
    s0:  EngineState[IO],
    put: IO[Unit],
    n:   Long
  ): IO[Option[EngineState[IO]]] =
    (put *> oe
      .stream(s0)
      .take(n)
      .compile
      .last)
      .map(_.map(_._2))

}

object TestCommon {

  given Logger[IO] = NoOpLogger.impl[IO]

  val defaultSettings: ObserveEngineConfiguration = ObserveEngineConfiguration(
    odb = uri"localhost",
    dhsServer = uri"http://localhost/",
    systemControl = SystemsControlConfiguration(
      altair = ControlStrategy.Simulated,
      gems = ControlStrategy.Simulated,
      dhs = ControlStrategy.Simulated,
      f2 = ControlStrategy.Simulated,
      gcal = ControlStrategy.Simulated,
      gmos = ControlStrategy.Simulated,
      gnirs = ControlStrategy.Simulated,
      gpi = ControlStrategy.Simulated,
      gpiGds = ControlStrategy.Simulated,
      ghost = ControlStrategy.Simulated,
      ghostGds = ControlStrategy.Simulated,
      gsaoi = ControlStrategy.Simulated,
      gws = ControlStrategy.Simulated,
      nifs = ControlStrategy.Simulated,
      niri = ControlStrategy.Simulated,
      tcs = ControlStrategy.Simulated
    ),
    odbNotifications = false,
    instForceError = false,
    failAt = 0,
    10.seconds,
    GpiUriSettings(uri"vm://localhost:8888/xmlrpc"),
    GpiUriSettings(uri"http://localhost:8888/xmlrpc"),
    GhostUriSettings(uri"vm://localhost:8888/xmlrpc"),
    GhostUriSettings(uri"http://localhost:8888/xmlrpc"),
    "",
    Some("127.0.0.1"),
    0,
    3.seconds,
    10.seconds,
    32
  )

  def configure[F[_]: Applicative](resource: Resource): F[Result] =
    Result.OK(Response.Configured(resource)).pure[F].widen

  def pendingAction[F[_]: Applicative](resource: Resource): Action[F] =
    engine.fromF[F](ActionType.Configure(resource), configure(resource))

  Action.State(Action.ActionState.Started, Nil)

  def running[F[_]: Applicative](resource: Resource): Action[F] =
    Action
      .state[F]
      .replace(Action.State(Action.ActionState.Started, Nil))(pendingAction(resource))

  def done[F[_]: Applicative](resource: Resource): Action[F] =
    Action
      .state[F]
      .replace(Action.State(Action.ActionState.Completed(Response.Configured(resource)), Nil))(
        pendingAction(resource)
      )

  private val fileId = toImageFileId("fileId")

  def observing[F[_]: Applicative]: Action[F] =
    Action
      .state[F]
      .replace(Action.State(Action.ActionState.Started, Nil))(
        engine.fromF[F](ActionType.Observe, Result.OK(Response.Observed(fileId)).pure[F].widen)
      )

  final case class PartialValue(s: String) extends PartialVal

  def observingPartial[F[_]: Applicative]: Action[F] =
    Action
      .state[F]
      .replace(Action.State(Action.ActionState.Started, Nil))(
        engine.fromF[F](ActionType.Observe,
                        Result.Partial(PartialValue("Value")).pure[F].widen,
                        Result.OK(Response.Ignored).pure[F].widen
        )
      )

  def fileIdReady[F[_]: Applicative]: Action[F] =
    Action
      .state[F]
      .replace(Action.State(Action.ActionState.Started, List(FileIdAllocated(fileId))))(observing)

  def observed[F[_]: Applicative]: Action[F] =
    Action
      .state[F]
      .replace(
        Action.State(Action.ActionState.Completed(Response.Observed(fileId)),
                     List(FileIdAllocated(fileId))
        )
      )(observing)

  def observePartial[F[_]: Applicative]: Action[F] =
    Action
      .state[F]
      .replace(Action.State(Action.ActionState.Started, List(FileIdAllocated(fileId))))(
        observingPartial
      )

  def paused[F[_]: Applicative]: Action[F] =
    Action
      .state[F]
      .replace(
        Action.State(Action.ActionState.Paused(new PauseContext {}), List(FileIdAllocated(fileId)))
      )(observing)

//   def testCompleted(oid: Observation.Id)(st: EngineState[IO]): Boolean =
//     st.sequences
//       .get(oid)
//       .exists(_.seq.status.isCompleted)
//
  // private val gpiSim: IO[GpiController[IO]] = GpiClient
  //   .simulatedGpiClient[IO]
  //   .use(x =>
  //     IO(
  //       GpiController(x, GdsClient(GdsClient.alwaysOkClient[IO], uri"http://localhost:8888/xmlrpc"))
  //     )
  //   )
  //
  // private val ghostSim: IO[GhostController[IO]] = GhostClient
  //   .simulatedGhostClient[IO]
  //   .use(x =>
  //     IO(
  //       GhostController(x,
  //                       GdsClient(GdsClient.alwaysOkClient[IO], uri"http://localhost:8888/xmlrpc")
  //       )
  //     )
  //   )
  //
  val seqName1: Observation.Name           = "GS-2018B-Q-0-1"
  val seqObsId1: Observation.Id            = observationId(1)
  val seqName2: Observation.Name           = "GS-2018B-Q-0-2"
  val seqObsId2: Observation.Id            = observationId(2)
  val seqName3: Observation.Name           = "GS-2018B-Q-0-3"
  val seqObsId3: Observation.Id            = observationId(3)
  val clientId: ClientId                   = ClientId(UUID.randomUUID)
  val atomId1: Atom.Id                     = Atom.Id(UUID.fromString("5fbe2ac4-5ba7-11ee-8c99-0242ac120002"))
  val stepId1: Step.Id                     = Step.Id(UUID.fromString("91806878-5173-4da9-ae2b-d7bf32407871"))
  val staticCfg1: StaticConfig.GmosNorth   = StaticConfig.GmosNorth(
    GmosNorthStageMode.FollowXyz,
    GmosNorthDetector.Hamamatsu,
    MosPreImaging.IsNotMosPreImaging,
    None
  )
  val dynamicCfg1: DynamicConfig.GmosNorth = DynamicConfig.GmosNorth(
    TimeSpan.unsafeFromMicroseconds(5000000000L),
    GmosCcdMode(
      GmosXBinning.Two,
      GmosYBinning.Two,
      GmosAmpCount.Three,
      GmosAmpGain.High,
      GmosAmpReadMode.Fast
    ),
    GmosDtax.Two,
    GmosRoi.CentralSpectrum,
    GmosGratingConfig
      .North(
        GmosNorthGrating.B600_G5303,
        GmosGratingOrder.One,
        Wavelength.unsafeFromIntPicometers(400000)
      )
      .some,
    None,
    GmosFpuMask.Builtin[GmosNorthFpu](GmosNorthFpu.LongSlit_0_50).some
  )

  val stepCfg1: StepConfig = StepConfig.Science(
    Offset.Zero,
    GuideState.Enabled
  )

  def sequence(id: Observation.Id): SequenceGen[IO] = SequenceGen[IO](
    ODBObservation(
      id = id,
      title = "",
      ObsStatus.Ready,
      ObsActiveStatus.Active,
      ODBObservation.Program(
        Program.Id(PosLong.unsafeFrom(123)),
        None
      ),
      TargetEnvironment(None),
      ConstraintSet(
        ImageQuality.PointOne,
        CloudExtinction.PointOne,
        SkyBackground.Dark,
        WaterVapor.Median,
        ElevationRange.AirMass.Default
      ),
      List.empty,
      Execution(
        GmosNorth(
          ExecutionConfig[StaticConfig.GmosNorth, DynamicConfig.GmosNorth](
            staticCfg1,
            ExecutionSequence[DynamicConfig.GmosNorth](
              Atom[DynamicConfig.GmosNorth](
                atomId1,
                None,
                NonEmptyList(
                  Step[DynamicConfig.GmosNorth](
                    stepId(1),
                    dynamicCfg1,
                    stepCfg1,
                    StepEstimate.Zero,
                    ObserveClass.Science,
                    Breakpoint.Disabled
                  ),
                  List.empty
                )
              ),
              List.empty,
              false
            ).some,
            None
          )
        )
      )
    ),
    instrument = Instrument.GmosN,
    staticCfg1,
    atomId1,
    steps = List(
      SequenceGen.PendingStepGen(
        id = stepId(1),
        Monoid.empty[DataId],
        resources = Set(Instrument.GmosN, Resource.TCS),
        _ => InstrumentSystem.Uncontrollable,
        generator = SequenceGen.StepActionsGen(
          configs = Map(),
          post = (_, _) => List(NonEmptyList.one(pendingAction[IO](Instrument.GmosN)))
        ),
        StepStatusGen.Null,
        dynamicCfg1,
        stepCfg1
      )
    )
  )

  def sequenceNSteps(id: Observation.Id, n: Int): SequenceGen[IO] = SequenceGen[IO](
    ODBObservation(
      id = id,
      title = "",
      ObsStatus.Ready,
      ObsActiveStatus.Active,
      ODBObservation.Program(
        Program.Id(PosLong.unsafeFrom(123)),
        None
      ),
      TargetEnvironment(None),
      ConstraintSet(
        ImageQuality.PointOne,
        CloudExtinction.PointOne,
        SkyBackground.Dark,
        WaterVapor.Median,
        ElevationRange.AirMass.Default
      ),
      List.empty,
      Execution(
        GmosNorth(
          ExecutionConfig[StaticConfig.GmosNorth, DynamicConfig.GmosNorth](
            staticCfg1,
            ExecutionSequence[DynamicConfig.GmosNorth](
              Atom[DynamicConfig.GmosNorth](
                atomId1,
                None,
                NonEmptyList(
                  Step[DynamicConfig.GmosNorth](
                    stepId(1),
                    dynamicCfg1,
                    stepCfg1,
                    StepEstimate.Zero,
                    ObserveClass.Science,
                    Breakpoint.Disabled
                  ),
                  List
                    .range(2, n)
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
              ),
              List.empty,
              false
            ).some,
            None
          )
        )
      )
    ),
    instrument = Instrument.GmosN,
    staticCfg1,
    atomId1,
    steps = List
      .range(1, n)
      .map(i =>
        SequenceGen.PendingStepGen(
          id = stepId(i),
          Monoid.empty[DataId],
          resources = Set(Instrument.GmosN, Resource.TCS),
          _ => InstrumentSystem.Uncontrollable,
          generator = SequenceGen.StepActionsGen(
            configs = Map(),
            post = (_, _) => List(NonEmptyList.one(pendingAction[IO](Instrument.GmosN)))
          ),
          StepStatusGen.Null,
          dynamicCfg1,
          stepCfg1
        )
      )
  )

  def sequenceWithResources(
    id:        Observation.Id,
    ins:       Instrument,
    resources: Set[Resource]
  ): SequenceGen[IO] = SequenceGen[IO](
    ODBObservation(
      id = id,
      title = "",
      ObsStatus.Ready,
      ObsActiveStatus.Active,
      ODBObservation.Program(
        Program.Id(PosLong.unsafeFrom(123)),
        None
      ),
      TargetEnvironment(None),
      ConstraintSet(
        ImageQuality.PointOne,
        CloudExtinction.PointOne,
        SkyBackground.Dark,
        WaterVapor.Median,
        ElevationRange.AirMass.Default
      ),
      List.empty,
      Execution(
        GmosNorth(
          ExecutionConfig[StaticConfig.GmosNorth, DynamicConfig.GmosNorth](
            staticCfg1,
            ExecutionSequence[DynamicConfig.GmosNorth](
              Atom[DynamicConfig.GmosNorth](
                atomId1,
                None,
                NonEmptyList(
                  Step[DynamicConfig.GmosNorth](
                    stepId(1),
                    dynamicCfg1,
                    stepCfg1,
                    StepEstimate.Zero,
                    ObserveClass.Science,
                    Breakpoint.Disabled
                  ),
                  List.empty
                )
              ),
              List.empty,
              false
            ).some,
            None
          )
        )
      )
    ),
    instrument = Instrument.GmosN,
    staticCfg1,
    atomId1,
    steps = List(
      SequenceGen.PendingStepGen(
        id = stepId(1),
        Monoid.empty[DataId],
        resources = resources,
        _ => InstrumentSystem.Uncontrollable,
        generator = SequenceGen.StepActionsGen(
          configs = resources.map(r => r -> { (_: SystemOverrides) => pendingAction[IO](r) }).toMap,
          post = (_, _) => Nil
        ),
        StepStatusGen.Null,
        dynamicCfg1,
        stepCfg1
      ),
      SequenceGen.PendingStepGen(
        id = stepId(2),
        Monoid.empty[DataId],
        resources = resources,
        _ => InstrumentSystem.Uncontrollable,
        generator = SequenceGen.StepActionsGen(
          configs = resources.map(r => r -> { (_: SystemOverrides) => pendingAction[IO](r) }).toMap,
          post = (_, _) => Nil
        ),
        StepStatusGen.Null,
        dynamicCfg1,
        stepCfg1
      )
    )
  )

}
