// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.*
import cats.data.NonEmptySet
import cats.effect.Async
import cats.syntax.all.*
import coulomb.ops.algebra.all.given
import coulomb.policy.standard.given
import lucuma.core.enums.ComaOption
import lucuma.core.enums.M1Source
import lucuma.core.enums.MountGuideOption
import lucuma.core.enums.Site
import lucuma.core.enums.TipTiltSource
import lucuma.core.model.GemsConfig
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M1GuideConfig.M1GuideOn
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.TimeSpan
import monocle.Focus
import monocle.Lens
import monocle.syntax.all.*
import mouse.boolean.*
import observe.server.EpicsCodex.encode
import observe.server.ObserveFailure
import observe.server.gems.Gems
import observe.server.tcs.Gaos.PauseCondition
import observe.server.tcs.Gaos.PauseConditionSet
import observe.server.tcs.Gaos.ResumeCondition
import observe.server.tcs.Gaos.ResumeConditionSet
import observe.server.tcs.GemsSource.*
import observe.server.tcs.TcsController.*
import observe.server.tcs.TcsControllerEpicsCommon.calcMoveDistanceSquared
import observe.server.tcs.TcsEpics.ProbeFollowCmd
import observe.server.tcs.TcsEpics.VirtualGemsTelescope
import observe.server.tcs.TcsSouthController.*
import org.typelevel.log4cats.Logger

/**
 * Controller of Gemini's South AO system over epics
 */
sealed trait TcsSouthControllerEpicsAo[F[_]] {
  def applyAoConfig(
    subsystems:   NonEmptySet[Subsystem],
    gaos:         Gems[F],
    baseAoConfig: GemsConfig,
    tcs:          TcsSouthAoConfig
  ): F[Unit]
}

object TcsSouthControllerEpicsAo {
  final case class EpicsTcsAoConfig(
    base:    BaseEpicsTcsConfig,
    mapping: Map[GemsSource, VirtualGemsTelescope],
    cwfs1:   GuiderConfig,
    cwfs2:   GuiderConfig,
    cwfs3:   GuiderConfig,
    odgw1:   GuiderConfig,
    odgw2:   GuiderConfig,
    odgw3:   GuiderConfig,
    odgw4:   GuiderConfig
  )

  object EpicsTcsAoConfig {
    val base: Lens[EpicsTcsAoConfig, BaseEpicsTcsConfig]           = EpicsTcsAoConfig.base
    val cwfs1: Lens[EpicsTcsAoConfig, GuiderConfig]                = Focus[EpicsTcsAoConfig](_.cwfs1)
    val cwfs2: Lens[EpicsTcsAoConfig, GuiderConfig]                = Focus[EpicsTcsAoConfig](_.cwfs2)
    val cwfs3: Lens[EpicsTcsAoConfig, GuiderConfig]                = Focus[EpicsTcsAoConfig](_.cwfs3)
    val odgw1Tracking: Lens[EpicsTcsAoConfig, ProbeTrackingConfig] =
      Focus[EpicsTcsAoConfig](_.odgw1.tracking)
    val odgw2Tracking: Lens[EpicsTcsAoConfig, ProbeTrackingConfig] =
      Focus[EpicsTcsAoConfig](_.odgw2.tracking)
    val odgw3Tracking: Lens[EpicsTcsAoConfig, ProbeTrackingConfig] =
      Focus[EpicsTcsAoConfig](_.odgw3.tracking)
    val odgw4Tracking: Lens[EpicsTcsAoConfig, ProbeTrackingConfig] =
      Focus[EpicsTcsAoConfig](_.odgw4.tracking)
  }

  private final class TcsSouthControllerEpicsAoImpl[F[_]: Async](epicsSys: TcsEpics[F])(using
    L: Logger[F]
  ) extends TcsSouthControllerEpicsAo[F]
      with TcsControllerEncoders {
    private val tcsConfigRetriever = TcsConfigRetriever[F](epicsSys)
    private val commonController   = TcsControllerEpicsCommon[F, Site.GN.type](epicsSys)
    private val trace              =
      Option(System.getProperty("observe.server.tcs.trace")).flatMap(_.toBooleanOption).isDefined

    def setNgsGuide(followCmd: ProbeFollowCmd[F], l: Lens[EpicsTcsAoConfig, GuiderConfig])(
      name:       String
    )(
      g:          VirtualGemsTelescope,
      subsystems: NonEmptySet[Subsystem],
      current:    ProbeTrackingConfig,
      demand:     ProbeTrackingConfig
    ): Option[WithDebug[EpicsTcsAoConfig => F[EpicsTcsAoConfig]]] =
      if (subsystems.contains(Subsystem.Gaos)) {
        val actionList = List(
          (current.getNodChop =!= demand.getNodChop).option(
            commonController
              .setNodChopProbeTrackingConfig(epicsSys.gemsProbeGuideCmd(g))(demand.getNodChop)
              .withDebug(s"NodChop(${current.getNodChop} =!= ${demand.getNodChop})")
          ),
          (current.follow =!= demand.follow).option(
            followCmd
              .setFollowState(encode(demand.follow))
              .withDebug(s"follow(${current.follow} =!= ${demand.follow})")
          )
        ).flattenOption

        val actions = actionList.reduceOption { (a, b) =>
          WithDebug(a.self *> b.self, a.debug + ", " + b.debug)
        }

        actions.map { r =>
          { (x: EpicsTcsAoConfig) =>
            r.self.as(l.andThen(GuiderConfig.tracking).replace(demand)(x))
          }.withDebug(s"$name tracking set because ${r.debug}")
        }
      } else none

    val setCwfs1Guide: (
      VirtualGemsTelescope,
      NonEmptySet[Subsystem],
      ProbeTrackingConfig,
      ProbeTrackingConfig
    ) => Option[WithDebug[EpicsTcsAoConfig => F[EpicsTcsAoConfig]]] =
      setNgsGuide(epicsSys.cwfs1ProbeFollowCmd, EpicsTcsAoConfig.cwfs1)("C1")

    val setCwfs2Guide: (
      VirtualGemsTelescope,
      NonEmptySet[Subsystem],
      ProbeTrackingConfig,
      ProbeTrackingConfig
    ) => Option[WithDebug[EpicsTcsAoConfig => F[EpicsTcsAoConfig]]] =
      setNgsGuide(epicsSys.cwfs2ProbeFollowCmd, EpicsTcsAoConfig.cwfs2)("C2")

    val setCwfs3Guide: (
      VirtualGemsTelescope,
      NonEmptySet[Subsystem],
      ProbeTrackingConfig,
      ProbeTrackingConfig
    ) => Option[WithDebug[EpicsTcsAoConfig => F[EpicsTcsAoConfig]]] =
      setNgsGuide(epicsSys.cwfs3ProbeFollowCmd, EpicsTcsAoConfig.cwfs3)("C3")

    private def odgw1GuiderControl(g: VirtualGemsTelescope): GuideControl[F] =
      GuideControl(Subsystem.Gaos,
                   epicsSys.odgw1ParkCmd,
                   epicsSys.gemsProbeGuideCmd(g),
                   epicsSys.odgw1FollowCmd
      )

    def setOdgw1Probe(g: VirtualGemsTelescope)(
      a: NonEmptySet[Subsystem],
      b: ProbeTrackingConfig,
      c: ProbeTrackingConfig
    ): Option[WithDebug[EpicsTcsAoConfig => F[EpicsTcsAoConfig]]] =
      commonController
        .setGuideProbe(odgw1GuiderControl(g), EpicsTcsAoConfig.odgw1Tracking.replace)(a, b, c)
        .map(_.mapDebug(m => s"ODGW1: $m"))

    private def odgw2GuiderControl(g: VirtualGemsTelescope): GuideControl[F] =
      GuideControl(Subsystem.Gaos,
                   epicsSys.odgw2ParkCmd,
                   epicsSys.gemsProbeGuideCmd(g),
                   epicsSys.odgw2FollowCmd
      )

    def setOdgw2Probe(g: VirtualGemsTelescope)(
      a: NonEmptySet[Subsystem],
      b: ProbeTrackingConfig,
      c: ProbeTrackingConfig
    ): Option[WithDebug[EpicsTcsAoConfig => F[EpicsTcsAoConfig]]] =
      commonController
        .setGuideProbe(odgw2GuiderControl(g), EpicsTcsAoConfig.odgw2Tracking.replace)(a, b, c)
        .map(_.mapDebug(m => s"ODGW2: $m"))

    private def odgw3GuiderControl(g: VirtualGemsTelescope): GuideControl[F] =
      GuideControl(Subsystem.Gaos,
                   epicsSys.odgw3ParkCmd,
                   epicsSys.gemsProbeGuideCmd(g),
                   epicsSys.odgw3FollowCmd
      )

    def setOdgw3Probe(g: VirtualGemsTelescope)(
      a: NonEmptySet[Subsystem],
      b: ProbeTrackingConfig,
      c: ProbeTrackingConfig
    ): Option[WithDebug[EpicsTcsAoConfig => F[EpicsTcsAoConfig]]] =
      commonController
        .setGuideProbe(odgw3GuiderControl(g), EpicsTcsAoConfig.odgw3Tracking.replace)(a, b, c)
        .map(_.mapDebug(m => s"ODGW3: $m"))

    private def odgw4GuiderControl(g: VirtualGemsTelescope): GuideControl[F] =
      GuideControl(Subsystem.Gaos,
                   epicsSys.odgw4ParkCmd,
                   epicsSys.gemsProbeGuideCmd(g),
                   epicsSys.odgw4FollowCmd
      )

    def setOdgw4Probe(g: VirtualGemsTelescope)(
      a: NonEmptySet[Subsystem],
      b: ProbeTrackingConfig,
      c: ProbeTrackingConfig
    ): Option[WithDebug[EpicsTcsAoConfig => F[EpicsTcsAoConfig]]] =
      commonController
        .setGuideProbe(odgw4GuiderControl(g), EpicsTcsAoConfig.odgw4Tracking.replace)(a, b, c)
        .map(_.mapDebug(m => s"ODGW4: $m"))

    def setGemsProbes(
      subsystems: NonEmptySet[Subsystem],
      current:    EpicsTcsAoConfig,
      demand:     GemsGuiders
    ): List[WithDebug[EpicsTcsAoConfig => F[EpicsTcsAoConfig]]] = List(
      current.mapping
        .get(Cwfs1)
        .flatMap(setCwfs1Guide(_, subsystems, current.cwfs1.tracking, demand.cwfs1.value.tracking)),
      current.mapping
        .get(Cwfs2)
        .flatMap(setCwfs2Guide(_, subsystems, current.cwfs2.tracking, demand.cwfs2.value.tracking)),
      current.mapping
        .get(Cwfs3)
        .flatMap(setCwfs3Guide(_, subsystems, current.cwfs3.tracking, demand.cwfs3.value.tracking)),
      current.mapping
        .get(Odgw1)
        .flatMap(setOdgw1Probe(_)(subsystems, current.odgw1.tracking, demand.odgw1.value.tracking)),
      current.mapping
        .get(Odgw2)
        .flatMap(setOdgw2Probe(_)(subsystems, current.odgw2.tracking, demand.odgw2.value.tracking)),
      current.mapping
        .get(Odgw3)
        .flatMap(setOdgw3Probe(_)(subsystems, current.odgw3.tracking, demand.odgw3.value.tracking)),
      current.mapping
        .get(Odgw4)
        .flatMap(setOdgw4Probe(_)(subsystems, current.odgw4.tracking, demand.odgw4.value.tracking))
    ).flattenOption

    // It will be a GeMS guided step only if all GeMS sources used in the base configuration are active
    private def isAoGuidedStep(baseAoCfg: GemsConfig, demand: TcsSouthAoConfig): Boolean =
      (!baseAoCfg.isCwfs1Used || demand.gds.aoguide.cwfs1.value.isActive) &&
        (!baseAoCfg.isCwfs2Used || demand.gds.aoguide.cwfs2.value.isActive) &&
        (!baseAoCfg.isCwfs3Used || demand.gds.aoguide.cwfs3.value.isActive) &&
        (!baseAoCfg.isOdgw1Used || demand.gds.aoguide.odgw1.value.isActive) &&
        (!baseAoCfg.isOdgw2Used || demand.gds.aoguide.odgw2.value.isActive) &&
        (!baseAoCfg.isOdgw3Used || demand.gds.aoguide.odgw3.value.isActive) &&
        (!baseAoCfg.isOdgw4Used || demand.gds.aoguide.odgw4.value.isActive) &&
        (!baseAoCfg.isOIUsed || demand.gds.oiwfs.value.isActive) &&
        (!baseAoCfg.isP1Used || demand.gds.pwfs1.value.isActive)

    // GeMS is guiding if all sources used in the GeMS base configuration are active
    private def isCurrentlyGuiding(current: EpicsTcsAoConfig, baseAoCfg: GemsConfig): Boolean =
      (!baseAoCfg.isCwfs1Used || current.cwfs1.isActive) &&
        (!baseAoCfg.isCwfs2Used || current.cwfs2.isActive) &&
        (!baseAoCfg.isCwfs3Used || current.cwfs3.isActive) &&
        (!baseAoCfg.isOdgw1Used || current.odgw1.isActive) &&
        (!baseAoCfg.isOdgw2Used || current.odgw2.isActive) &&
        (!baseAoCfg.isOdgw3Used || current.odgw3.isActive) &&
        (!baseAoCfg.isOdgw4Used || current.odgw4.isActive) &&
        (!baseAoCfg.isOIUsed || current.base.oiwfs.isActive) &&
        (!baseAoCfg.isP1Used || current.base.pwfs1.isActive)

    private def isStartingUnguidedStep(
      current:   EpicsTcsAoConfig,
      baseAoCfg: GemsConfig,
      demand:    TcsSouthAoConfig
    ): Boolean =
      isCurrentlyGuiding(current, baseAoCfg) && !isAoGuidedStep(baseAoCfg, demand)

    private def isComingBackFromUnguidedStep(
      current:   EpicsTcsAoConfig,
      baseAoCfg: GemsConfig,
      demand:    TcsSouthAoConfig
    ): Boolean =
      !isCurrentlyGuiding(current, baseAoCfg) && isAoGuidedStep(baseAoCfg, demand)

    private def mustPauseAoWhileOffseting(
      current: EpicsTcsAoConfig,
      demand:  TcsSouthAoConfig
    ): Boolean = {
      val distanceSquared = calcMoveDistanceSquared(current.base, demand.tc)

      val isAnyGemsSourceUsed = (demand.gaos.isCwfs1Used && current.cwfs1.isActive) ||
        (demand.gaos.isCwfs2Used && !current.cwfs2.isActive) ||
        (demand.gaos.isCwfs3Used && !current.cwfs3.isActive) ||
        (demand.gaos.isOdgw1Used && !current.odgw1.isActive) ||
        (demand.gaos.isOdgw2Used && !current.odgw2.isActive) ||
        (demand.gaos.isOdgw3Used && !current.odgw3.isActive) ||
        (demand.gaos.isOdgw4Used && !current.odgw4.isActive)

      distanceSquared.exists(dd =>
        (isAnyGemsSourceUsed && dd > AoOffsetThreshold.pow[2]) ||
          (demand.gaos.isP1Used && dd > pwfs1OffsetThreshold.pow[2]) ||
          (demand.gaos.isOIUsed && demand.inst.oiOffsetGuideThreshold.exists(t => dd > t.pow[2]))
      )

    }

    private def mustPauseWhileOffsetting(
      current: EpicsTcsAoConfig,
      demand:  TcsSouthAoConfig
    ): Boolean = {
      val distanceSquared = calcMoveDistanceSquared(current.base, demand.tc)

      val becauseP1 = distanceSquared.exists(dd =>
        Tcs.calcGuiderInUse(demand.gc, TipTiltSource.PWFS1, M1Source.PWFS1)
          && dd > pwfs1OffsetThreshold.pow[2]
      )

      val becauseOi = demand.inst.oiOffsetGuideThreshold.exists(t =>
        Tcs.calcGuiderInUse(demand.gc, TipTiltSource.OIWFS, M1Source.OIWFS) && distanceSquared
          .exists(_ > t.pow[2])
      )

      val becauseAo = demand.gc.m1Guide match {
        case M1GuideOn(M1Source.GAOS) => mustPauseAoWhileOffseting(current, demand)
        case _                        => false
      }

      becauseOi || becauseP1 || becauseAo
    }

    def calcAoPauseConditions(
      current:      EpicsTcsAoConfig,
      baseAoConfig: GemsConfig,
      demand:       TcsSouthAoConfig
    ): PauseConditionSet =
      PauseConditionSet.fromList(
        List(
          isStartingUnguidedStep(current, baseAoConfig, demand).option(PauseCondition.GaosGuideOff),
          demand.tc.offsetA.flatMap(o =>
            (isAoGuidedStep(baseAoConfig, demand) && mustPauseAoWhileOffseting(
              current,
              demand
            ))
              .option(
                PauseCondition.OffsetMove(current.base.offset,
                                          o.toFocalPlaneOffset(current.base.iaa)
                )
              )
          )
        ).flattenOption
      )

    def calcAoResumeConditions(
      current:      EpicsTcsAoConfig,
      baseAoConfig: GemsConfig,
      demand:       TcsSouthAoConfig,
      pauseReasons: PauseConditionSet
    ): ResumeConditionSet =
      ResumeConditionSet.fromList(
        List(
          isComingBackFromUnguidedStep(current, baseAoConfig, demand).option(
            ResumeCondition.GaosGuideOn
          ),
          pauseReasons.offsetO.map(o => ResumeCondition.OffsetReached(o.to))
        ).flattenOption
      )

    def pauseResumeGaos(
      gaos:         Gems[F],
      current:      EpicsTcsAoConfig,
      baseAoConfig: GemsConfig,
      demand:       TcsSouthAoConfig
    ): F[Gaos.PauseResume[F]] = {
      val pauseReasons = calcAoPauseConditions(current, baseAoConfig, demand)
      gaos.pauseResume(
        pauseReasons,
        calcAoResumeConditions(current, baseAoConfig, demand, pauseReasons)
      )
    }

    def calcGuideOff(
      current:     EpicsTcsAoConfig,
      demand:      TcsSouthAoConfig,
      gaosEnabled: Boolean
    ): TcsSouthAoConfig = {
      val mustOff                                            = mustPauseWhileOffsetting(current, demand)
      // Only turn things off here. Things that must be turned on will be turned on in GuideOn.
      def calc(c: GuiderSensorOption, d: GuiderSensorOption) =
        (mustOff || d === GuiderSensorOff).fold(GuiderSensorOff, c)

      (AoTcsConfig
        .gds[Site.GS.type]
        .modify(
          AoGuidersConfig
            .pwfs1[GemsGuiders]
            .andThen(TcsController.P1Config.Value)
            .andThen(GuiderConfig.detector)
            .replace(calc(current.base.pwfs1.detector, demand.gds.pwfs1.value.detector)) >>>
            AoGuidersConfig
              .oiwfs[GemsGuiders]
              .andThen(TcsController.OIConfig.Value)
              .andThen(GuiderConfig.detector)
              .replace(calc(current.base.oiwfs.detector, demand.gds.oiwfs.value.detector))
        ) >>>
        AoTcsConfig.gc
          .modify(
            TelescopeGuideConfig.mountGuide.replace(
              (mustOff || demand.gc.mountGuide === MountGuideOption.MountGuideOff)
                .fold(MountGuideOption.MountGuideOff, current.base.telescopeGuideConfig.mountGuide)
            ) >>>
              TelescopeGuideConfig.m1Guide.replace(
                (mustOff || demand.gc.m1Guide === M1GuideConfig.M1GuideOff)
                  .fold(M1GuideConfig.M1GuideOff, current.base.telescopeGuideConfig.m1Guide)
              ) >>>
              TelescopeGuideConfig.m2Guide.replace(
                (mustOff || demand.gc.m2Guide === M2GuideConfig.M2GuideOff)
                  .fold(M2GuideConfig.M2GuideOff, current.base.telescopeGuideConfig.m2Guide)
              )
          ) >>> normalizeM1Guiding >>> normalizeM2Guiding(gaosEnabled) >>> normalizeMountGuiding)(
        demand
      )

    }

    def guideParams(
      subsystems: NonEmptySet[Subsystem],
      current:    EpicsTcsAoConfig,
      demand:     TcsSouthAoConfig
    ): List[WithDebug[EpicsTcsAoConfig => F[EpicsTcsAoConfig]]] = List(
      commonController.setMountGuide(EpicsTcsAoConfig.base)(
        subsystems,
        current.base.telescopeGuideConfig.mountGuide,
        demand.gc.mountGuide
      ),
      commonController.setM1Guide(EpicsTcsAoConfig.base)(
        subsystems,
        current.base.telescopeGuideConfig.m1Guide,
        demand.gc.m1Guide
      ),
      commonController.setM2Guide(EpicsTcsAoConfig.base)(
        subsystems,
        current.base.telescopeGuideConfig.m2Guide,
        demand.gc.m2Guide
      ),
      commonController.setPwfs1(EpicsTcsAoConfig.base)(subsystems,
                                                       current.base.pwfs1.detector,
                                                       demand.gds.pwfs1.value.detector
      ),
      commonController.setOiwfs(EpicsTcsAoConfig.base)(subsystems,
                                                       current.base.oiwfs.detector,
                                                       demand.gds.oiwfs.value.detector
      )
    ).flattenOption

    def guideOff(
      subsystems: NonEmptySet[Subsystem],
      current:    EpicsTcsAoConfig,
      demand:     TcsSouthAoConfig,
      pauseGaos:  Boolean
    ): F[EpicsTcsAoConfig] = {
      val paramList = guideParams(subsystems, current, calcGuideOff(current, demand, pauseGaos))

      if (paramList.nonEmpty) {
        val params = paramList.foldLeft(current.pure[F]) { case (c, p) => c.flatMap(p.self) }
        val debug  = paramList.map(_.debug).mkString(", ")
        for {
          _ <- L.debug("Turning guide off")
          _ <- L.debug(s"guideOff set because $debug").whenA(trace)
          s <- params
          _ <- epicsSys.post(TcsControllerEpicsCommon.DefaultTimeout)
          _ <- L.debug("Guide turned off")
        } yield s
      } else
        L.debug("Skipping guide off") *> current.pure[F]

    }

    def guideOn(
      subsystems:  NonEmptySet[Subsystem],
      current:     EpicsTcsAoConfig,
      demand:      TcsSouthAoConfig,
      gaosEnabled: Boolean
    ): F[EpicsTcsAoConfig] = {
      // If the demand turned off any WFS, normalize will turn off the corresponding processing
      val normalizedGuiding = (normalizeM1Guiding >>> normalizeM2Guiding(gaosEnabled) >>>
        normalizeMountGuiding)(demand)

      val paramList = guideParams(subsystems, current, normalizedGuiding)

      if (paramList.nonEmpty) {
        val params = paramList.foldLeft(current.pure[F]) { case (c, p) => c.flatMap(p.self) }
        val debug  = paramList.map(_.debug).mkString(", ")
        for {
          _ <- L.debug("Turning guide on")
          _ <- L.debug(s"guideOn set because $debug").whenA(trace)
          s <- params
          _ <- epicsSys.post(TcsControllerEpicsCommon.DefaultTimeout)
          _ <- L.debug("Guide turned on")
        } yield s
      } else
        L.debug("Skipping guide on") *> current.pure[F]
    }

    def applyAoConfig(
      subsystems:   NonEmptySet[Subsystem],
      gaos:         Gems[F],
      baseAoConfig: GemsConfig,
      tcs:          TcsSouthAoConfig
    ): F[Unit] = {
      def configParams(
        current: EpicsTcsAoConfig
      ): List[WithDebug[EpicsTcsAoConfig => F[EpicsTcsAoConfig]]] =
        List(
          commonController.setPwfs1Probe(EpicsTcsAoConfig.base)(
            subsystems,
            current.base.pwfs1.tracking,
            tcs.gds.pwfs1.value.tracking
          ),
          commonController.setOiwfsProbe(EpicsTcsAoConfig.base)(
            subsystems,
            current.base.oiwfs.tracking,
            tcs.gds.oiwfs.value.tracking,
            current.base.oiName,
            tcs.inst.instrument
          ),
          commonController
            .setScienceFold(EpicsTcsAoConfig.base)(subsystems, current, tcs.agc.sfPos),
          commonController.setHrPickup(EpicsTcsAoConfig.base)(subsystems, current, tcs.agc)
        ).flattenOption ++ setGemsProbes(subsystems, current, tcs.gds.aoguide)

      def sysConfig(current: EpicsTcsAoConfig): F[EpicsTcsAoConfig] = {
        val mountConfigParams   =
          commonController.configMountPos(subsystems, current, tcs.tc, EpicsTcsAoConfig.base)
        val paramList           = configParams(current) ++ mountConfigParams
        val mountMoves: Boolean = mountConfigParams.nonEmpty
        val stabilizationTime   = tcs.tc.offsetA
          .map(
            TcsSettleTimeCalculator
              .calc(current.base.instrumentOffset, _, subsystems, tcs.inst.instrument)
          )
          .orEmpty

        if (paramList.nonEmpty) {
          val params = paramList.foldLeft(current.pure[F]) { case (c, p) => c.flatMap(p.self) }
          val debug  = paramList.map(_.debug).mkString(", ")
          for {
            _ <- L.debug("Start TCS configuration")
            _ <- L.debug(s"TCS configuration: ${tcs.show}")
            _ <- L.debug(s"for subsystems $subsystems")
            _ <- L.debug(s"TCS set because $debug").whenA(trace)
            s <- params
            _ <- epicsSys.post(TcsControllerEpicsCommon.ConfigTimeout)
            _ <-
              if (mountMoves)
                epicsSys.waitInPosition(stabilizationTime, tcsTimeout) *> L.debug("TCS inposition")
              else if (
                Set(Subsystem.PWFS1, Subsystem.PWFS2, Subsystem.AGUnit).exists(
                  subsystems.contains
                )
              )
                epicsSys.waitAGInPosition(agTimeout) *> L.debug("AG inposition")
              else Applicative[F].unit
            _ <- L.debug("Completed TCS configuration")
          } yield s
        } else
          L.debug("Skipping TCS configuration") *> current.pure[F]
      }

      for {
        s0 <- tcsConfigRetriever.retrieveConfigurationSouth(gaos.stateGetter)
        _  <- ObserveFailure
                .Execution("Found useAo not set for GeMS step.")
                .raiseError[F, Unit]
                .whenA(!s0.base.useAo)
        pr <- pauseResumeGaos(gaos, s0, baseAoConfig, tcs)
        _  <- pr.pause.getOrElse(Applicative[F].unit)
        s1 <- guideOff(subsystems, s0, tcs, pr.pause.isEmpty)
        s2 <- sysConfig(s1)
        _  <- guideOn(subsystems, s2, tcs, pr.resume.isDefined)
        _  <- pr.resume.getOrElse(Applicative[F].unit)
      } yield ()
    }

    // Disable M1 guiding if source is off
    def normalizeM1Guiding: Endo[TcsSouthAoConfig] = cfg =>
      cfg
        .focus(_.gc.m1Guide)
        .modify {
          case g @ M1GuideConfig.M1GuideOn(src) =>
            src match {
              case M1Source.PWFS1 =>
                if (cfg.gds.pwfs1.value.isActive) g else M1GuideConfig.M1GuideOff
              case M1Source.OIWFS =>
                if (cfg.gds.oiwfs.value.isActive) g else M1GuideConfig.M1GuideOff
              case _              => g
            }
          case x                                => x
        }

    // Disable M2 sources if they are off, disable M2 guiding if all are off
    def normalizeM2Guiding(gaosEnabled: Boolean): Endo[TcsSouthAoConfig] = cfg =>
      cfg
        .focus(_.gc.m2Guide)
        .modify {
          case M2GuideConfig.M2GuideOn(coma, srcs) =>
            val ss = srcs.filter {
              case TipTiltSource.PWFS1 => cfg.gds.pwfs1.value.isActive
              case TipTiltSource.OIWFS => cfg.gds.oiwfs.value.isActive
              case TipTiltSource.GAOS  => gaosEnabled
              case _                   => true
            }
            if (ss.isEmpty) M2GuideConfig.M2GuideOff
            else
              M2GuideConfig.M2GuideOn(if (cfg.gc.m1Guide =!= M1GuideConfig.M1GuideOff) coma
                                      else ComaOption.ComaOff,
                                      ss
              )
          case x                                   => x
        }
  }

  // Disable Mount guiding if M2 guiding is disabled
  val normalizeMountGuiding: Endo[TcsSouthAoConfig] = cfg =>
    cfg
      .focus(_.gc.mountGuide)
      .modify { m =>
        (m, cfg.gc.m2Guide) match {
          case (MountGuideOption.MountGuideOn, M2GuideConfig.M2GuideOn(_, _)) =>
            MountGuideOption.MountGuideOn
          case _                                                              => MountGuideOption.MountGuideOff
        }
      }

  def apply[F[_]: Async: Logger](epicsSys: TcsEpics[F]): TcsSouthControllerEpicsAo[F] =
    new TcsSouthControllerEpicsAoImpl(epicsSys)

}
