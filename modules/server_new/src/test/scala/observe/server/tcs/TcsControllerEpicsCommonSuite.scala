// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import algebra.instances.all.given
import cats.effect.Async
import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import coulomb.Quantity
import coulomb.policy.standard.given
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import coulomb.units.accepted.Millimeter
import edu.gemini.observe.server.tcs.BinaryOnOff
import edu.gemini.observe.server.tcs.BinaryYesNo
import lucuma.core.enums.Instrument
import lucuma.core.enums.LightSinkName.Gmos
import lucuma.core.enums.Site
import lucuma.core.math.Angle
import lucuma.core.math.Wavelength
import lucuma.core.math.Wavelength.*
import lucuma.core.syntax.all.*
import observe.model.M1GuideConfig
import observe.model.M2GuideConfig
import observe.model.TelescopeGuideConfig
import observe.model.enums.ComaOption
import observe.model.enums.M1Source
import observe.model.enums.MountGuideOption
import observe.model.enums.TipTiltSource
import observe.server.InstrumentGuide
import observe.server.keywords.USLocale
import observe.server.tcs.FocalPlaneScale.*
import observe.server.tcs.TcsController.BasicTcsConfig
import observe.server.tcs.TcsController.LightSource.Sky
import observe.server.tcs.TcsController.*
import observe.server.tcs.TestTcsEpics.ProbeGuideConfigVals
import observe.server.tcs.TestTcsEpics.TestTcsEvent
import observe.server.tcs.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import scala.language.implicitConversions

class TcsControllerEpicsCommonSuite extends munit.FunSuite {

  import TcsControllerEpicsCommonSuite.*

  private given Logger[IO] = NoOpLogger.impl[IO]

  private val baseCurrentStatus = BaseEpicsTcsConfig(
    Angle.fromDoubleArcseconds(33.8),
    FocalPlaneOffset(OffsetX(0.0.withUnit[Millimeter]), OffsetY(0.0.withUnit[Millimeter])),
    Wavelength.fromIntMicrometers(400).get,
    GuiderConfig(ProbeTrackingConfig.Off, GuiderSensorOff),
    GuiderConfig(ProbeTrackingConfig.Off, GuiderSensorOff),
    GuiderConfig(ProbeTrackingConfig.Off, GuiderSensorOff),
    "None",
    TelescopeGuideConfig(MountGuideOption.MountGuideOff,
                         M1GuideConfig.M1GuideOff,
                         M2GuideConfig.M2GuideOff
    ),
    AoFold.Out,
    useAo = false,
    None,
    HrwfsPickupPosition.OUT,
    InstrumentPorts(
      flamingos2Port = 5,
      ghostPort = 0,
      gmosPort = 3,
      gnirsPort = 0,
      gpiPort = 0,
      gsaoiPort = 1,
      nifsPort = 0,
      niriPort = 0
    )
  )

  private val baseConfig = BasicTcsConfig[Site.GS.type](
    TelescopeGuideConfig(MountGuideOption.MountGuideOff,
                         M1GuideConfig.M1GuideOff,
                         M2GuideConfig.M2GuideOff
    ),
    TelescopeConfig(None, None),
    BasicGuidersConfig(
      P1Config(GuiderConfig(ProbeTrackingConfig.Off, GuiderSensorOff)),
      P2Config(GuiderConfig(ProbeTrackingConfig.Off, GuiderSensorOff)),
      OIConfig(GuiderConfig(ProbeTrackingConfig.Off, GuiderSensorOff))
    ),
    AGConfig(LightPath(Sky, Gmos), None),
    DummyInstrument(Instrument.GmosSouth, 1.0.withUnit[Millimeter].some)
  )

  def doubleInstrumentOffset(threshold: Quantity[Double, Millimeter]): InstrumentOffset =
    InstrumentOffset(OffsetP((threshold * 2) ** FOCAL_PLANE_SCALE),
                     OffsetQ(0.0.withUnit[ArcSecond])
    )

  def halfInstrumentOffset(threshold: Quantity[Double, Millimeter]): InstrumentOffset =
    InstrumentOffset(OffsetP((threshold / 2) ** FOCAL_PLANE_SCALE),
                     OffsetQ(0.0.withUnit[ArcSecond])
    )

  val ioPwfs1: InstrumentOffset      = doubleInstrumentOffset(pwfs1OffsetThreshold)
  val ioSmallPwfs1: InstrumentOffset = halfInstrumentOffset(pwfs1OffsetThreshold)

  val ioPwfs2: InstrumentOffset      = doubleInstrumentOffset(pwfs2OffsetThreshold)
  val ioSmallPwfs2: InstrumentOffset = halfInstrumentOffset(pwfs2OffsetThreshold)

  test("TcsControllerEpicsCommon should not pause guiding if it is not necessary") {
    // No offset
    assert(
      !TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        baseConfig
      )
    )

    // Offset, but no guider in use
    assert(
      !TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        BasicTcsConfig.offsetALensGS
          .replace(ioPwfs1.some)(baseConfig)
      )
    )
  }

  test(
    "TcsControllerEpicsCommon should decide if it can keep PWFS1 guiding active when applying an offset"
  ) {
    // Big offset with PWFS1 in use
    assert(
      TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(ioPwfs1.some) >>>
            BasicTcsConfig.m2GuideLensGS
              .replace(
                M2GuideConfig.M2GuideOn(ComaOption.ComaOff, Set(TipTiltSource.PWFS1))
              ) >>>
            BasicTcsConfig.pwfs1LensGS
              .replace(
                P1Config(
                  GuiderConfig(
                    ProbeTrackingConfig.On(NodChopTrackingConfig.Normal),
                    GuiderSensorOn
                  )
                )
              )
        )(baseConfig)
      )
    )

    assert(
      TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(ioPwfs1.some) >>>
            BasicTcsConfig.m1GuideLensGS
              .replace(
                M1GuideConfig.M1GuideOn(M1Source.PWFS1)
              ) >>>
            BasicTcsConfig.pwfs1LensGS
              .replace(
                P1Config(
                  GuiderConfig(
                    ProbeTrackingConfig.On(NodChopTrackingConfig.Normal),
                    GuiderSensorOn
                  )
                )
              )
        )(baseConfig)
      )
    )

    // Small offset with PWFS1 in use
    assert(
      !TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(ioSmallPwfs1.some) >>>
            BasicTcsConfig.m2GuideLensGS
              .replace(
                M2GuideConfig.M2GuideOn(ComaOption.ComaOff, Set(TipTiltSource.PWFS1))
              ) >>>
            BasicTcsConfig.pwfs1LensGS
              .replace(
                P1Config(
                  GuiderConfig(
                    ProbeTrackingConfig.On(NodChopTrackingConfig.Normal),
                    GuiderSensorOn
                  )
                )
              )
        )(baseConfig)
      )
    )
  }

  test(
    "TcsControllerEpicsCommon should decide if it can keep PWFS2 guiding active when applying an offset"
  ) {
    // Big offset with PWFS2 in use
    assert(
      TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(ioPwfs2.some) >>>
            BasicTcsConfig.m2GuideLensGS
              .replace(
                M2GuideConfig.M2GuideOn(ComaOption.ComaOff, Set(TipTiltSource.PWFS2))
              ) >>>
            BasicTcsConfig.pwfs2LensGS
              .replace(
                P2Config(
                  GuiderConfig(
                    ProbeTrackingConfig.On(NodChopTrackingConfig.Normal),
                    GuiderSensorOn
                  )
                )
              )
        )(baseConfig)
      )
    )

    assert(
      TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(ioPwfs2.some) >>>
            BasicTcsConfig.m1GuideLensGS
              .replace(
                M1GuideConfig.M1GuideOn(M1Source.PWFS2)
              ) >>>
            BasicTcsConfig.pwfs2LensGS
              .replace(
                P2Config(
                  GuiderConfig(
                    ProbeTrackingConfig.On(NodChopTrackingConfig.Normal),
                    GuiderSensorOn
                  )
                )
              )
        )(baseConfig)
      )
    )

    // Small offset with PWFS2 in use
    assert(
      !TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(ioSmallPwfs2.some) >>>
            BasicTcsConfig.m2GuideLensGS
              .replace(
                M2GuideConfig.M2GuideOn(ComaOption.ComaOff, Set(TipTiltSource.PWFS2))
              ) >>>
            BasicTcsConfig.pwfs2LensGS
              .replace(
                P2Config(
                  GuiderConfig(
                    ProbeTrackingConfig.On(NodChopTrackingConfig.Normal),
                    GuiderSensorOn
                  )
                )
              )
        )(baseConfig)
      )
    )

  }

  test(
    "TcsControllerEpicsCommon should decide if it can keep OIWFS guiding active when applying an offset"
  ) {
    val threshold           = 1.0.withUnit[Millimeter]
    val thresholdOffset     = doubleInstrumentOffset(threshold)
    val halfThresholdOffset = halfInstrumentOffset(threshold)

    // Big offset with OIWFS in use
    assert(
      TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(thresholdOffset.some) >>>
            BasicTcsConfig.m2GuideLensGS
              .replace(
                M2GuideConfig.M2GuideOn(ComaOption.ComaOff, Set(TipTiltSource.OIWFS))
              ) >>>
            BasicTcsConfig.oiwfsLensGS
              .replace(
                OIConfig(
                  GuiderConfig(
                    ProbeTrackingConfig.On(NodChopTrackingConfig.Normal),
                    GuiderSensorOn
                  )
                )
              ) >>>
            BasicTcsConfig.instLensGS
              .replace(DummyInstrument(Instrument.GmosSouth, threshold.some))
        )(baseConfig)
      )
    )

    assert(
      TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(thresholdOffset.some) >>>
            BasicTcsConfig.m1GuideLensGS
              .replace(
                M1GuideConfig.M1GuideOn(M1Source.OIWFS)
              ) >>>
            BasicTcsConfig.oiwfsLensGS
              .replace(
                OIConfig(
                  GuiderConfig(
                    ProbeTrackingConfig.On(NodChopTrackingConfig.Normal),
                    GuiderSensorOn
                  )
                )
              ) >>>
            BasicTcsConfig.instLensGS
              .replace(DummyInstrument(Instrument.GmosSouth, threshold.some))
        )(baseConfig)
      )
    )

    // Small offset with OIWFS in use
    assert(
      !TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(halfThresholdOffset.some) >>>
            BasicTcsConfig.m2GuideLensGS
              .replace(
                M2GuideConfig.M2GuideOn(ComaOption.ComaOff, Set(TipTiltSource.OIWFS))
              ) >>>
            BasicTcsConfig.oiwfsLensGS
              .replace(
                OIConfig(
                  GuiderConfig(
                    ProbeTrackingConfig.On(NodChopTrackingConfig.Normal),
                    GuiderSensorOn
                  )
                )
              ) >>>
            BasicTcsConfig.instLensGS
              .replace(DummyInstrument(Instrument.GmosSouth, threshold.some))
        )(baseConfig)
      )
    )
  }

  private val baseStateWithP1Guiding = TestTcsEpics.defaultState.copy(
    absorbTipTilt = 1,
    m1GuideSource = "PWFS1",
    m1Guide = BinaryOnOff.On,
    m2GuideState = BinaryOnOff.On,
    m2p1Guide = "ON",
    p1FollowS = "On",
    p1Parked = false,
    pwfs1On = BinaryYesNo.Yes,
    sfName = "gmos3",
    pwfs1ProbeGuideConfig = ProbeGuideConfigVals(1, 0, 0, 1),
    gmosPort = 3,
    comaCorrect = "On"
  )

  test("TcsControllerEpicsCommon should not open PWFS1 loops if configuration does not change") {
    val dumbEpics = buildTcsController[IO](baseStateWithP1Guiding)

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      gc = TelescopeGuideConfig(
        MountGuideOption.MountGuideOn,
        M1GuideConfig.M1GuideOn(M1Source.PWFS1),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.PWFS1))
      ),
      gds = baseConfig.gds.copy(
        pwfs1 = P1Config(
          GuiderConfig(ProbeTrackingConfig.On(NodChopTrackingConfig.Normal), GuiderSensorOn)
        )
      )
    )

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaosNorOi, config)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    assert(result.isEmpty)

  }

  private val guideOffEvents = List(
    TestTcsEvent.M1GuideCmd("off"),
    TestTcsEvent.M2GuideCmd("off"),
    TestTcsEvent.M2GuideConfigCmd("", "", "on"),
    TestTcsEvent.MountGuideCmd("", "off")
  )

  private val guideOnEvents = List(
    TestTcsEvent.M1GuideCmd("on"),
    TestTcsEvent.M2GuideCmd("on"),
    TestTcsEvent.MountGuideCmd("", "on")
  )

  test("TcsControllerEpicsCommon should open PWFS1 loops for an unguided configuration") {
    val dumbEpics = buildTcsController[IO](baseStateWithP1Guiding)

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaosNorOi, baseConfig)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    (List(
      TestTcsEvent.Pwfs1StopObserveCmd,
      TestTcsEvent.Pwfs1ProbeFollowCmd("Off"),
      TestTcsEvent.Pwfs1ProbeGuideConfig("Off", "Off", "Off", "Off")
    ) ++ guideOffEvents).map(result.contains(_)).foreach(assert(_))

  }

  test("TcsControllerEpicsCommon should open and close PWFS1 loops for a big enough offset") {
    val dumbEpics = buildTcsController[IO](baseStateWithP1Guiding)

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      tc = baseConfig.tc
        .copy(offsetA =
          InstrumentOffset(OffsetP(10.0.withUnit[ArcSecond]), OffsetQ(0.0.withUnit[ArcSecond])).some
        ),
      gc = TelescopeGuideConfig(
        MountGuideOption.MountGuideOn,
        M1GuideConfig.M1GuideOn(M1Source.PWFS1),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.PWFS1))
      ),
      gds = baseConfig.gds.copy(
        pwfs1 = P1Config(
          GuiderConfig(ProbeTrackingConfig.On(NodChopTrackingConfig.Normal), GuiderSensorOn)
        )
      )
    )

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaosNorOi, config)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    val (head, tail) = result.span {
      case TestTcsEvent.OffsetACmd(_, _) => false
      case _                             => true
    }

    guideOffEvents.foreach(a => assert(head.contains(a)))

    List(
      TestTcsEvent.Pwfs1StopObserveCmd,
      TestTcsEvent.Pwfs1ProbeFollowCmd("Off"),
      TestTcsEvent.Pwfs1ProbeGuideConfig("Off", "Off", "Off", "Off")
    ).foreach(a => assert(!head.contains(a)))

    guideOnEvents.foreach(a => assert(tail.contains(a)))

  }

  test("TcsControllerEpicsCommon should close PWFS1 loops for a guided configuration") {
    // Current Tcs state with PWFS1 guiding, but off
    val dumbEpics = buildTcsController[IO](
      TestTcsEpics.defaultState.copy(
        m1GuideSource = "PWFS1",
        m2p1Guide = "ON",
        p1Parked = false,
        sfName = "gmos3",
        gmosPort = 3
      )
    )

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      gc = TelescopeGuideConfig(
        MountGuideOption.MountGuideOn,
        M1GuideConfig.M1GuideOn(M1Source.PWFS1),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.PWFS1))
      ),
      gds = baseConfig.gds.copy(
        pwfs1 = P1Config(
          GuiderConfig(ProbeTrackingConfig.On(NodChopTrackingConfig.Normal), GuiderSensorOn)
        )
      )
    )

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaosNorOi, config)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    (List(
      TestTcsEvent.Pwfs1ObserveCmd,
      TestTcsEvent.Pwfs1ProbeFollowCmd("On"),
      TestTcsEvent.Pwfs1ProbeGuideConfig("On", "Off", "Off", "On")
    ) ++ guideOnEvents).foreach(a => assert(result.contains(a)))

  }

  val baseStateWithP2Guiding = TestTcsEpics.defaultState.copy(
    absorbTipTilt = 1,
    m1GuideSource = "PWFS2",
    m1Guide = BinaryOnOff.On,
    m2GuideState = BinaryOnOff.On,
    m2p2Guide = "ON",
    p2FollowS = "On",
    p2Parked = false,
    pwfs2On = BinaryYesNo.Yes,
    sfName = "gmos3",
    pwfs2ProbeGuideConfig = ProbeGuideConfigVals(1, 0, 0, 1),
    gmosPort = 3,
    comaCorrect = "On"
  )

  test("TcsControllerEpicsCommon should not open PWFS2 loops if configuration does not change") {

    val dumbEpics = buildTcsController[IO](baseStateWithP2Guiding)

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      gc = TelescopeGuideConfig(
        MountGuideOption.MountGuideOn,
        M1GuideConfig.M1GuideOn(M1Source.PWFS2),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.PWFS2))
      ),
      gds = baseConfig.gds.copy(
        pwfs2 = P2Config(
          GuiderConfig(ProbeTrackingConfig.On(NodChopTrackingConfig.Normal), GuiderSensorOn)
        )
      )
    )

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaosNorOi, config)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    assert(result.isEmpty)

  }

  test("TcsControllerEpicsCommon should open PWFS2 loops for an unguided configuration") {
    val dumbEpics = buildTcsController[IO](baseStateWithP2Guiding)

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaosNorOi, baseConfig)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    (List(
      TestTcsEvent.Pwfs2StopObserveCmd,
      TestTcsEvent.Pwfs2ProbeFollowCmd("Off"),
      TestTcsEvent.Pwfs2ProbeGuideConfig("Off", "Off", "Off", "Off")
    ) ++ guideOffEvents).foreach(a => assert(result.contains(a)))

  }

  test("TcsControllerEpicsCommon should open and close PWFS2 loops for a big enough offset") {
    val dumbEpics = buildTcsController[IO](baseStateWithP2Guiding)

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      tc = baseConfig.tc
        .copy(offsetA =
          InstrumentOffset(OffsetP(10.0.withUnit[ArcSecond]), OffsetQ(0.0.withUnit[ArcSecond])).some
        ),
      gc = TelescopeGuideConfig(
        MountGuideOption.MountGuideOn,
        M1GuideConfig.M1GuideOn(M1Source.PWFS2),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.PWFS2))
      ),
      gds = baseConfig.gds.copy(
        pwfs2 = P2Config(
          GuiderConfig(ProbeTrackingConfig.On(NodChopTrackingConfig.Normal), GuiderSensorOn)
        )
      )
    )

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaosNorOi, config)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    val (head, tail) = result.span {
      case TestTcsEvent.OffsetACmd(_, _) => false
      case _                             => true
    }

    guideOffEvents.foreach(a => assert(head.contains(a)))

    List(
      TestTcsEvent.Pwfs2StopObserveCmd,
      TestTcsEvent.Pwfs2ProbeFollowCmd("Off"),
      TestTcsEvent.Pwfs2ProbeGuideConfig("Off", "Off", "Off", "Off")
    ).foreach(a => assert(!head.contains(a)))

    guideOnEvents.foreach(a => assert(tail.contains(a)))

  }

  test("TcsControllerEpicsCommon should close PWFS2 loops for a guided configuration") {
    // Current Tcs state with PWFS2 guiding, but off
    val dumbEpics = buildTcsController[IO](
      TestTcsEpics.defaultState.copy(
        m1GuideSource = "PWFS2",
        m2p2Guide = "ON",
        p2Parked = false,
        sfName = "gmos3",
        gmosPort = 3
      )
    )

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      gc = TelescopeGuideConfig(
        MountGuideOption.MountGuideOn,
        M1GuideConfig.M1GuideOn(M1Source.PWFS2),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.PWFS2))
      ),
      gds = baseConfig.gds.copy(
        pwfs2 = P2Config(
          GuiderConfig(ProbeTrackingConfig.On(NodChopTrackingConfig.Normal), GuiderSensorOn)
        )
      )
    )

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaosNorOi, config)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    (List(
      TestTcsEvent.Pwfs2ObserveCmd,
      TestTcsEvent.Pwfs2ProbeFollowCmd("On"),
      TestTcsEvent.Pwfs2ProbeGuideConfig("On", "Off", "Off", "On")
    ) ++ guideOnEvents).foreach(a => assert(result.contains(a)))

  }

  val baseStateWithOIGuiding = TestTcsEpics.defaultState.copy(
    absorbTipTilt = 1,
    m1GuideSource = "OIWFS",
    m1Guide = BinaryOnOff.On,
    m2GuideState = BinaryOnOff.On,
    m2oiGuide = "ON",
    oiFollowS = "On",
    oiParked = false,
    oiwfsOn = BinaryYesNo.Yes,
    sfName = "gmos3",
    oiwfsProbeGuideConfig = ProbeGuideConfigVals(1, 0, 0, 1),
    oiName = "GMOS",
    gmosPort = 3,
    comaCorrect = "On"
  )

  test("TcsControllerEpicsCommon should not open OIWFS loops if configuration does not change") {

    val dumbEpics = buildTcsController[IO](baseStateWithOIGuiding)

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      gc = TelescopeGuideConfig(
        MountGuideOption.MountGuideOn,
        M1GuideConfig.M1GuideOn(M1Source.OIWFS),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS))
      ),
      gds = baseConfig.gds.copy(
        oiwfs = OIConfig(
          GuiderConfig(ProbeTrackingConfig.On(NodChopTrackingConfig.Normal), GuiderSensorOn)
        )
      )
    )

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaos, config)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    assert(result.isEmpty)

  }

  test("TcsControllerEpicsCommon should open OIWFS loops for an unguided configuration") {
    val dumbEpics = buildTcsController[IO](baseStateWithOIGuiding)

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaos, baseConfig)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    (List(
      TestTcsEvent.OiwfsStopObserveCmd,
      TestTcsEvent.OiwfsProbeFollowCmd("Off"),
      TestTcsEvent.OiwfsProbeGuideConfig("Off", "Off", "Off", "Off")
    ) ++ guideOffEvents).foreach(a => assert(result.contains(a)))

  }

  test("TcsControllerEpicsCommon should open and close OIWFS loops for a big enough offset") {
    val dumbEpics = buildTcsController[IO](baseStateWithOIGuiding)

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      tc = baseConfig.tc
        .copy(offsetA =
          InstrumentOffset(OffsetP(10.0.withUnit[ArcSecond]), OffsetQ(0.0.withUnit[ArcSecond])).some
        ),
      gc = TelescopeGuideConfig(
        MountGuideOption.MountGuideOn,
        M1GuideConfig.M1GuideOn(M1Source.OIWFS),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS))
      ),
      gds = baseConfig.gds.copy(
        oiwfs = OIConfig(
          GuiderConfig(ProbeTrackingConfig.On(NodChopTrackingConfig.Normal), GuiderSensorOn)
        )
      )
    )

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaos, config)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    val (head, tail) = result.span {
      case TestTcsEvent.OffsetACmd(_, _) => false
      case _                             => true
    }

    guideOffEvents.foreach(a => assert(head.contains(a)))

    List(
      TestTcsEvent.OiwfsStopObserveCmd,
      TestTcsEvent.OiwfsProbeFollowCmd("Off"),
      TestTcsEvent.OiwfsProbeGuideConfig("Off", "Off", "Off", "Off")
    ).foreach(a => assert(!head.contains(a)))

    guideOnEvents.foreach(a => assert(tail.contains(a)))

  }

  test("TcsControllerEpicsCommon should close OIWFS loops for a guided configuration") {
    // Current Tcs state with OIWFS guiding, but off
    val dumbEpics = buildTcsController[IO](
      TestTcsEpics.defaultState.copy(
        m1GuideSource = "OIWFS",
        m2oiGuide = "ON",
        oiParked = false,
        oiName = "GMOS",
        sfName = "gmos3",
        gmosPort = 3
      )
    )

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      gc = TelescopeGuideConfig(
        MountGuideOption.MountGuideOn,
        M1GuideConfig.M1GuideOn(M1Source.OIWFS),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS))
      ),
      gds = baseConfig.gds.copy(
        oiwfs = OIConfig(
          GuiderConfig(ProbeTrackingConfig.On(NodChopTrackingConfig.Normal), GuiderSensorOn)
        )
      )
    )

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaos, config)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    (List(
      TestTcsEvent.OiwfsObserveCmd,
      TestTcsEvent.OiwfsProbeFollowCmd("On"),
      TestTcsEvent.OiwfsProbeGuideConfig("On", "Off", "Off", "On")
    ) ++ guideOnEvents).foreach(a => assert(result.contains(a)))

  }

  // This function simulates the loss of precision in the EPICS record
  def epicsTransform(prec: Int)(v: Double): Double =
    s"%.${prec}f".formatLocal(USLocale, v).toDouble

  test("TcsControllerEpicsCommon should apply an offset if it is not at the right position") {

    val offsetDemand =
      InstrumentOffset(OffsetP(10.0.withUnit[ArcSecond]), OffsetQ(-5.0.withUnit[ArcSecond]))

    val offsetCurrent =
      InstrumentOffset(OffsetP(10.00001.withUnit[ArcSecond]), OffsetQ(-5.0.withUnit[ArcSecond]))
    val iaa           = 33.degrees
    val wavelength    = Wavelength.fromIntNanometers(440).get
    val recordPrec    = 14

    val dumbEpics = buildTcsController[IO](
      TestTcsEpics.defaultState.copy(
        xoffsetPoA1 =
          epicsTransform(recordPrec)(offsetCurrent.toFocalPlaneOffset(iaa).x.value.value),
        yoffsetPoA1 =
          epicsTransform(recordPrec)(offsetCurrent.toFocalPlaneOffset(iaa).y.value.value),
        instrAA = epicsTransform(recordPrec)(iaa.toDoubleDegrees),
        sourceAWavelength =
          epicsTransform(recordPrec)(wavelength.toAngstroms.value.value.doubleValue)
      )
    )

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      tc = TelescopeConfig(offsetDemand.some, wavelength.some)
    )

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaos, config)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    assert(
      result.exists {
        case TestTcsEvent.OffsetACmd(_, _) => true
        case _                             => false
      }
    )

  }

  test(
    "TcsControllerEpicsCommon should not reapply an offset if it is already at the right position"
  ) {

    val offset     =
      InstrumentOffset(OffsetP(10.0.withUnit[ArcSecond]), OffsetQ(-5.0.withUnit[ArcSecond]))
    val iaa        = 33.degrees
    val wavelength = Wavelength.fromIntNanometers(440).get
    val recordPrec = 14

    val dumbEpics = buildTcsController[IO](
      TestTcsEpics.defaultState.copy(
        xoffsetPoA1 = epicsTransform(recordPrec)(offset.toFocalPlaneOffset(iaa).x.value.value),
        yoffsetPoA1 = epicsTransform(recordPrec)(offset.toFocalPlaneOffset(iaa).y.value.value),
        instrAA = epicsTransform(recordPrec)(iaa.toDoubleDegrees),
        sourceAWavelength =
          epicsTransform(recordPrec)(wavelength.toAngstroms.value.value.doubleValue)
      )
    )

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      tc = TelescopeConfig(offset.some, wavelength.some)
    )

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaos, config)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    assert(
      !result.exists {
        case TestTcsEvent.OffsetACmd(_, _) => true
        case _                             => false
      }
    )

  }

  test("TcsControllerEpicsCommon should apply the target wavelength if it changes") {

    val wavelengthDemand: Wavelength  = Wavelength.unsafeFromIntPicometers(1000000)
    val wavelengthCurrent: Wavelength = Wavelength.unsafeFromIntPicometers(1001000)
    val recordPrec                    = 0

    val dumbEpics = buildTcsController[IO](
      TestTcsEpics.defaultState.copy(
        sourceAWavelength =
          epicsTransform(recordPrec)(wavelengthCurrent.toAngstroms.value.value.doubleValue)
      )
    )

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      tc = TelescopeConfig(None, wavelengthDemand.some)
    )

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaos, config)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    assert(
      result.exists {
        case TestTcsEvent.WavelSourceACmd(_) => true
        case _                               => false
      }
    )

  }

  test(
    "TcsControllerEpicsCommon should not reapply the target wavelength if it is already at the right value"
  ) {

    val wavelength = Wavelength.fromIntNanometers((2000.0 / 7.0).toInt).get
    val recordPrec = 0

    val dumbEpics = buildTcsController[IO](
      TestTcsEpics.defaultState.copy(
        sourceAWavelength =
          epicsTransform(recordPrec)(wavelength.toAngstroms.value.value.doubleValue)
      )
    )

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      tc = TelescopeConfig(None, wavelength.some)
    )

    val genOut: IO[List[TestTcsEpics.TestTcsEvent]] = for {
      d <- dumbEpics
      c  = TcsControllerEpicsCommon[IO, Site.GS.type](d)
      _ <- c.applyBasicConfig(TcsController.Subsystem.allButGaos, config)
      r <- d.outputF
    } yield r

    val result = genOut.unsafeRunSync()

    assert(
      !result.exists {
        case TestTcsEvent.WavelSourceACmd(_) => true
        case _                               => false
      }
    )

  }

}

object TcsControllerEpicsCommonSuite {
  case class DummyInstrument(id: Instrument, threshold: Option[Quantity[Double, Millimeter]])
      extends InstrumentGuide {
    override val instrument: Instrument = id

    override def oiOffsetGuideThreshold: Option[Quantity[Double, Millimeter]] = threshold
  }

  def buildTcsController[F[_]: Async](baseState: TestTcsEpics.State): F[TestTcsEpics[F]] =
    for {
      stR  <- Ref.of[F, TestTcsEpics.State](baseState)
      outR <- Ref.of[F, List[TestTcsEpics.TestTcsEvent]](List.empty)
    } yield TestTcsEpics[F](stR, outR)
}
