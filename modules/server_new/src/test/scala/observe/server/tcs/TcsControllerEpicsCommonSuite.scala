// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.effect.{Async, IO}
import cats.syntax.all.*
import cats.effect.unsafe.implicits.global
import edu.gemini.observe.server.tcs.{BinaryOnOff, BinaryYesNo}
import lucuma.core.math.Wavelength
import lucuma.core.math.Wavelength.*
import lucuma.core.enums.LightSinkName.Gmos
import lucuma.core.enums.Site
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import observe.model.{M1GuideConfig, M2GuideConfig, TelescopeGuideConfig}
import observe.model.enums.{ComaOption, Instrument, M1Source, MountGuideOption, TipTiltSource}
import observe.server.InstrumentGuide
import observe.server.tcs.TcsController.BasicTcsConfig
import observe.server.tcs.TcsController.LightSource.Sky
import observe.server.tcs.TcsController.*
import squants.space.{Arcseconds, Length, Millimeters}
import observe.server.keywords.USLocale
import observe.server.tcs.TestTcsEpics.{ProbeGuideConfigVals, TestTcsEvent}
import squants.space.AngleConversions.*
import squants.space.LengthConversions.*

import cats.effect.Ref

class TcsControllerEpicsCommonSuite extends munit.FunSuite {

  import TcsControllerEpicsCommonSpec.*

  private given Logger[IO] = NoOpLogger.impl[IO]

  private val baseCurrentStatus = BaseEpicsTcsConfig(
    Arcseconds(33.8),
    FocalPlaneOffset(OffsetX(Millimeters(0.0)), OffsetY(Millimeters(0.0))),
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
    DummyInstrument(Instrument.GmosS, 1.millimeters.some)
  )

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
          .replace(
            InstrumentOffset(
              OffsetP(pwfs1OffsetThreshold * 2 * FOCAL_PLANE_SCALE),
              OffsetQ(Arcseconds(0.0))
            ).some
          )(baseConfig)
      )
    )
  }

  test("decide if it can keep PWFS1 guiding active when applying an offset") {
    // Big offset with PWFS1 in use
    assert(
      TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(
              InstrumentOffset(
                OffsetP(pwfs1OffsetThreshold * 2.0 * FOCAL_PLANE_SCALE),
                OffsetQ(Arcseconds(0.0))
              ).some
            ) >>>
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
            .replace(
              InstrumentOffset(
                OffsetP(pwfs1OffsetThreshold * 2.0 * FOCAL_PLANE_SCALE),
                OffsetQ(Arcseconds(0.0))
              ).some
            ) >>>
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
            .replace(
              InstrumentOffset(
                OffsetP(pwfs1OffsetThreshold / 2.0 * FOCAL_PLANE_SCALE),
                OffsetQ(Arcseconds(0.0))
              ).some
            ) >>>
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

  test("decide if it can keep PWFS2 guiding active when applying an offset") {
    // Big offset with PWFS2 in use
    assert(
      TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(
              InstrumentOffset(
                OffsetP(pwfs2OffsetThreshold * 2.0 * FOCAL_PLANE_SCALE),
                OffsetQ(Arcseconds(0.0))
              ).some
            ) >>>
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
            .replace(
              InstrumentOffset(
                OffsetP(pwfs2OffsetThreshold * 2.0 * FOCAL_PLANE_SCALE),
                OffsetQ(Arcseconds(0.0))
              ).some
            ) >>>
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
            .replace(
              InstrumentOffset(
                OffsetP(pwfs2OffsetThreshold / 2.0 * FOCAL_PLANE_SCALE),
                OffsetQ(Arcseconds(0.0))
              ).some
            ) >>>
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

  test("decide if it can keep OIWFS guiding active when applying an offset") {
    val threshold = Millimeters(1.0)

    // Big offset with OIWFS in use
    assert(
      TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(
              InstrumentOffset(
                OffsetP(threshold * 2.0 * FOCAL_PLANE_SCALE),
                OffsetQ(Arcseconds(0.0))
              ).some
            ) >>>
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
              .replace(DummyInstrument(Instrument.GmosS, threshold.some))
        )(baseConfig)
      )
    )

    assert(
      TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(
              InstrumentOffset(
                OffsetP(threshold * 2.0 * FOCAL_PLANE_SCALE),
                OffsetQ(Arcseconds(0.0))
              ).some
            ) >>>
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
              .replace(DummyInstrument(Instrument.GmosS, threshold.some))
        )(baseConfig)
      )
    )

    // Small offset with OIWFS in use
    assert(
      !TcsControllerEpicsCommon.mustPauseWhileOffsetting(
        baseCurrentStatus,
        (
          BasicTcsConfig.offsetALensGS
            .replace(
              InstrumentOffset(
                OffsetP(threshold / 2.0 * FOCAL_PLANE_SCALE),
                OffsetQ(Arcseconds(0.0))
              ).some
            ) >>>
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
              .replace(DummyInstrument(Instrument.GmosS, threshold.some))
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

  test("open PWFS1 loops for an unguided configuration") {
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

  test("open and close PWFS1 loops for a big enough offset") {
    val dumbEpics = buildTcsController[IO](baseStateWithP1Guiding)

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      tc = baseConfig.tc
        .copy(offsetA = InstrumentOffset(OffsetP(10.arcseconds), OffsetQ(0.arcseconds)).some),
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

  test("close PWFS1 loops for a guided configuration") {
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

  test("not open PWFS2 loops if configuration does not change") {

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

  test("open PWFS2 loops for an unguided configuration") {
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

  test("open and close PWFS2 loops for a big enough offset") {
    val dumbEpics = buildTcsController[IO](baseStateWithP2Guiding)

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      tc = baseConfig.tc
        .copy(offsetA = InstrumentOffset(OffsetP(10.arcseconds), OffsetQ(0.arcseconds)).some),
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

  test("close PWFS2 loops for a guided configuration") {
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

  test("not open OIWFS loops if configuration does not change") {

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

  test("open OIWFS loops for an unguided configuration") {
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

  test("open and close OIWFS loops for a big enough offset") {
    val dumbEpics = buildTcsController[IO](baseStateWithOIGuiding)

    val config: BasicTcsConfig[Site.GS.type] = baseConfig.copy(
      tc = baseConfig.tc
        .copy(offsetA = InstrumentOffset(OffsetP(10.arcseconds), OffsetQ(0.arcseconds)).some),
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

  test("close OIWFS loops for a guided configuration") {
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

  test("apply an offset if it is not at the right position") {

    val offsetDemand  = InstrumentOffset(OffsetP(10.arcseconds), OffsetQ(-5.arcseconds))
    val offsetCurrent =
      InstrumentOffset(OffsetP(10.00001.arcseconds), OffsetQ(-5.arcseconds))
    val iaa           = 33.degrees
    val wavelength    = Wavelength.fromIntNanometers(440).get
    val recordPrec    = 14

    val dumbEpics = buildTcsController[IO](
      TestTcsEpics.defaultState.copy(
        xoffsetPoA1 =
          epicsTransform(recordPrec)(offsetCurrent.toFocalPlaneOffset(iaa).x.value.toMillimeters),
        yoffsetPoA1 =
          epicsTransform(recordPrec)(offsetCurrent.toFocalPlaneOffset(iaa).y.value.toMillimeters),
        instrAA = epicsTransform(recordPrec)(iaa.toDegrees),
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

  test("not reapply an offset if it is already at the right position") {

    val offset     = InstrumentOffset(OffsetP(10.arcseconds), OffsetQ(-5.arcseconds))
    val iaa        = 33.degrees
    val wavelength = Wavelength.fromIntNanometers(440).get
    val recordPrec = 14

    val dumbEpics = buildTcsController[IO](
      TestTcsEpics.defaultState.copy(
        xoffsetPoA1 =
          epicsTransform(recordPrec)(offset.toFocalPlaneOffset(iaa).x.value.toMillimeters),
        yoffsetPoA1 =
          epicsTransform(recordPrec)(offset.toFocalPlaneOffset(iaa).y.value.toMillimeters),
        instrAA = epicsTransform(recordPrec)(iaa.toDegrees),
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

  test("apply the target wavelength if it changes") {

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

  test("not reapply the target wavelength if it is already at the right value") {

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

object TcsControllerEpicsCommonSpec {
  final case class DummyInstrument(id: Instrument, threshold: Option[Length])
      extends InstrumentGuide {
    override val instrument: Instrument = id

    override def oiOffsetGuideThreshold: Option[Length] = threshold
  }

  def buildTcsController[F[_]: Async](baseState: TestTcsEpics.State): F[TestTcsEpics[F]] =
    for {
      stR  <- Ref.of[F, TestTcsEpics.State](baseState)
      outR <- Ref.of[F, List[TestTcsEpics.TestTcsEvent]](List.empty)
    } yield TestTcsEpics[F](stR, outR)
}
