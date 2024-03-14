// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Applicative
import cats.Monoid
import cats.data.NonEmptyList
import cats.effect.Async
import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all.*
import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.GmosAmpCount
import lucuma.core.enums.GmosAmpGain
import lucuma.core.enums.GmosAmpReadMode
import lucuma.core.enums.GmosDtax
import lucuma.core.enums.GmosGratingOrder
import lucuma.core.enums.GmosNorthDetector
import lucuma.core.enums.GmosNorthFpu
import lucuma.core.enums.GmosNorthGrating
import lucuma.core.enums.GmosNorthStageMode
import lucuma.core.enums.GmosRoi
import lucuma.core.enums.GmosXBinning
import lucuma.core.enums.GmosYBinning
import lucuma.core.enums.GuideState
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.Instrument
import lucuma.core.enums.MosPreImaging
import lucuma.core.enums.ObsActiveStatus
import lucuma.core.enums.ObsStatus
import lucuma.core.enums.ObserveClass
import lucuma.core.enums.SequenceType
import lucuma.core.enums.Site
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.math.Offset
import lucuma.core.math.Wavelength
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
import lucuma.core.model.sequence.gmos.GmosCcdMode
import lucuma.core.model.sequence.gmos.GmosFpuMask
import lucuma.core.model.sequence.gmos.GmosGratingConfig
import lucuma.core.model.sequence.gmos.StaticConfig
import lucuma.core.util.TimeSpan
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.Execution
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation as ODBObservation
import observe.common.test.*
import observe.engine
import observe.engine.Action
import observe.engine.Response
import observe.engine.Result
import observe.engine.Result.PartialVal
import observe.engine.Result.PauseContext
import observe.model.ActionType
import observe.model.ClientId
import observe.model.Conditions
import observe.model.Observation
import observe.model.SequenceState
import observe.model.SystemOverrides
import observe.model.config.*
import observe.model.dhs.*
import observe.model.enums.Resource
import observe.server.SequenceGen.StepStatusGen
import observe.server.odb.OdbProxy
import observe.server.odb.OdbProxy.DummyOdbProxy
import org.http4s.Uri
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import java.util.UUID
import scala.concurrent.duration.*

trait TestCommon extends munit.CatsEffectSuite {
  import TestCommon.*
  given Logger[IO] = NoOpLogger.impl[IO]

  val defaultSystems: IO[Systems[IO]] = Systems.dummy[IO]

  val observeEngine: IO[ObserveEngine[IO]] =
    defaultSystems.flatMap(ObserveEngine.build(Site.GS, _, defaultSettings))

  def observeEngineWithODB(odb: OdbProxy[IO]): IO[ObserveEngine[IO]] =
    defaultSystems.flatMap(sys =>
      ObserveEngine.build(Site.GS, sys.copy(odb = odb), defaultSettings)
    )

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

  def isFinished(status: SequenceState): Boolean = status match {
    case SequenceState.Idle      => true
    case SequenceState.Completed => true
    case SequenceState.Failed(_) => true
    case _                       => false
  }

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

  def configure[F[_]: Applicative](resource: Resource | Instrument): F[Result] =
    Result.OK(Response.Configured(resource)).pure[F].widen

  def pendingAction[F[_]: Applicative](resource: Resource | Instrument): Action[F] =
    engine.fromF[F](ActionType.Configure(resource), configure(resource))

  def odbAction[F[_]: Applicative]: Action[F] =
    engine.fromF(ActionType.OdbEvent, Result.OK(Response.Ignored).pure[F])

  def running[F[_]: Applicative](resource: Resource | Instrument): Action[F] =
    Action
      .state[F]
      .replace(Action.State(Action.ActionState.Started, Nil))(
        pendingAction(resource)
      )

  def done[F[_]: Applicative](resource: Resource | Instrument): Action[F] =
    Action
      .state[F]
      .replace(Action.State(Action.ActionState.Completed(Response.Configured(resource)), Nil))(
        pendingAction(resource)
      )

  private val fileId = ImageFileId("fileId")

  def observing[F[_]: Applicative]: Action[F] =
    Action
      .state[F]
      .replace(Action.State(Action.ActionState.Started, Nil))(
        engine.fromF[F](ActionType.Observe, Result.OK(Response.Observed(fileId)).pure[F].widen)
      )

  case class PartialValue(s: String) extends PartialVal

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
      .replace(
        Action.State(Action.ActionState.Started, List(FileIdAllocated(fileId)))
      )(observing)

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
      .replace(
        Action.State(Action.ActionState.Started, List(FileIdAllocated(fileId)))
      )(
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

  def odbObservation(id: Observation.Id, stepCount: Int = 1): ODBObservation =
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
              ),
              List.empty,
              false
            ).some,
            None
          )
        ).some
      )
    )

  def sequence(id: Observation.Id): SequenceGen[IO] = SequenceGen[IO](
    odbObservation(id, 1),
    instrument = Instrument.GmosNorth,
    staticCfg1,
    SequenceGen.AtomGen(
      atomId1,
      SequenceType.Science,
      steps = List(
        SequenceGen.PendingStepGen(
          id = stepId(1),
          Monoid.empty[DataId],
          resources = Set(Instrument.GmosNorth, Resource.TCS),
          _ => InstrumentSystem.Uncontrollable,
          generator = SequenceGen.StepActionsGen(
            odbAction[IO],
            odbAction[IO],
            configs = Map(),
            odbAction[IO],
            odbAction[IO],
            post = (_, _) => List(NonEmptyList.one(pendingAction[IO](Instrument.GmosNorth))),
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
  )

  def sequenceNSteps(id: Observation.Id, n: Int): SequenceGen[IO] = SequenceGen[IO](
    odbObservation(id, n),
    instrument = Instrument.GmosNorth,
    staticCfg1,
    SequenceGen.AtomGen(
      atomId1,
      SequenceType.Science,
      steps = List
        .range(1, n + 1)
        .map(i =>
          SequenceGen.PendingStepGen(
            id = stepId(i),
            Monoid.empty[DataId],
            resources = Set(Instrument.GmosNorth, Resource.TCS),
            _ => InstrumentSystem.Uncontrollable,
            generator = SequenceGen.StepActionsGen(
              odbAction[IO],
              odbAction[IO],
              configs = Map(),
              odbAction[IO],
              odbAction[IO],
              post = (_, _) => List(NonEmptyList.one(pendingAction[IO](Instrument.GmosNorth))),
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
  )

  def generateSequence[F[_]: Async: Logger](
    obs:     ODBObservation,
    systems: Systems[F]
  ): F[Option[SequenceGen[F]]] = for {
    c  <- Ref.of[F, Conditions](Conditions.Default)
    st <- SeqTranslate(Site.GS, systems, c)
    sg <- st.sequence(obs)
  } yield sg._2

  def sequenceWithResources(
    id:        Observation.Id,
    ins:       Instrument,
    resources: Set[Resource | Instrument]
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
        ).some
      )
    ),
    instrument = Instrument.GmosNorth,
    staticCfg1,
    SequenceGen.AtomGen(
      atomId1,
      SequenceType.Science,
      steps = List(
        SequenceGen.PendingStepGen(
          id = stepId(1),
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
          dynamicCfg1,
          stepCfg1,
          breakpoint = Breakpoint.Disabled
        ),
        SequenceGen.PendingStepGen(
          id = stepId(2),
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
          dynamicCfg1,
          stepCfg1,
          breakpoint = Breakpoint.Disabled
        )
      )
    )
  )

}
