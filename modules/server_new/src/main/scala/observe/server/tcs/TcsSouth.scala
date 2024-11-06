// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.data.NonEmptySet
import cats.effect.Sync
import cats.syntax.all.*
import coulomb.*
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import lucuma.core.enums.M1Source
import lucuma.core.enums.Site
import lucuma.core.enums.StepGuideState
import lucuma.core.enums.TipTiltSource
import lucuma.core.math.Angle
import lucuma.core.math.Offset
import lucuma.core.math.Wavelength
import lucuma.core.model.GemsConfig
import lucuma.core.model.GemsConfig.*
import lucuma.core.model.GuideConfig
import lucuma.core.model.sequence.TelescopeConfig as CoreTelescopeConfig
import mouse.all.*
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment
import observe.model.enums.NodAndShuffleStage
import observe.model.enums.Resource
import observe.server.ConfigResult
import observe.server.InstrumentGuide
import observe.server.ObserveFailure
import observe.server.gems.Gems
import observe.server.tcs.TcsController.*
import observe.server.tcs.TcsSouthController.*
import org.typelevel.log4cats.Logger

case class TcsSouth[F[_]: Sync: Logger] private (
  tcsController: TcsSouthController[F],
  subsystems:    NonEmptySet[Subsystem],
  gaos:          Option[Gems[F]],
  guideDb:       GuideConfigDb[F]
)(config: TcsSouth.TcsSeqConfig[F])
    extends Tcs[F] {
  import Tcs.*

  val Log: Logger[F] = Logger[F]

  override val resource: Resource = Resource.TCS

  override def configure: F[ConfigResult[F]] =
    buildTcsConfig.flatMap { cfg =>
      tcsController.applyConfig(subsystems, gaos, cfg).as(ConfigResult(this))
    }

  override def notifyObserveStart: F[Unit] = tcsController.notifyObserveStart

  override def notifyObserveEnd: F[Unit] = tcsController.notifyObserveEnd

  override def nod(
    stage:  NodAndShuffleStage,
    offset: InstrumentOffset,
    guided: Boolean
  ): F[ConfigResult[F]] =
    buildTcsConfig
      .flatMap { cfg =>
        Log.debug(s"Moving to nod ${stage.symbol}") *>
          tcsController.nod(subsystems, cfg)(stage, offset, guided)
      }
      .as(ConfigResult(this))

  val defaultGuiderConf = GuiderConfig(ProbeTrackingConfig.Parked, GuiderSensorOff)
  def calcGuiderConfig(
    inUse:     Boolean,
    guideWith: Option[StepGuideState]
  ): GuiderConfig =
    guideWith
      .flatMap(v => inUse.option(GuiderConfig(v.toProbeTracking, v.toGuideSensorOption)))
      .getOrElse(defaultGuiderConf)

  /*
   * Build TCS configuration for the step, merging the guide configuration from the sequence with the guide
   * configuration set from TCC. The TCC configuration has precedence: if a guider is not used in the TCC configuration,
   * it will not be used for the step, regardless of the sequence values.
   */
  def buildBasicTcsConfig(gc: GuideConfig): F[TcsSouthConfig] =
    (BasicTcsConfig(
      gc.tcsGuide,
      TelescopeConfig(config.offsetA, config.wavelA),
      BasicGuidersConfig(
        P1Config(
          calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.PWFS1, M1Source.PWFS1),
                           config.guideWithP1
          )
        ),
        P2Config(
          calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.PWFS2, M1Source.PWFS2),
                           config.guideWithP2
          )
        ),
        OIConfig(
          calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.OIWFS, M1Source.OIWFS),
                           config.guideWithOI
          )
        )
      ),
      AGConfig(config.lightPath, HrwfsConfig.Auto.some),
      config.instrument
    ): TcsSouthConfig).pure[F]

  private def anyGeMSGuiderActive(gc: TcsSouth.TcsSeqConfig[F]): Boolean =
    gc.guideWithCWFS1.exists(_ === StepGuideState.Enabled) ||
      gc.guideWithCWFS2.exists(_ === StepGuideState.Enabled) ||
      gc.guideWithCWFS3.exists(_ === StepGuideState.Enabled) ||
      gc.guideWithODGW1.exists(_ === StepGuideState.Enabled) ||
      gc.guideWithODGW2.exists(_ === StepGuideState.Enabled) ||
      gc.guideWithODGW3.exists(_ === StepGuideState.Enabled) ||
      gc.guideWithODGW4.exists(_ === StepGuideState.Enabled)

  private def buildTcsAoConfig(gc: GuideConfig): F[TcsSouthConfig] =
    gc.gaosGuide
      .flatMap(_.toOption)
      .fold {
        // Only raise an error if there is no GeMS config coming from TCS and step has a GeMS guider active.
        if (anyGeMSGuiderActive(config))
          ObserveFailure
            .Execution("Attempting to run GeMS sequence before GeMS was configured.")
            .raiseError[F, GemsConfig]
        else
          GemsOff.pure[F].widen[GemsConfig]
      }(_.pure[F])
      .map { aog =>
        AoTcsConfig[Site.GS.type](
          gc.tcsGuide,
          TelescopeConfig(config.offsetA, config.wavelA),
          AoGuidersConfig[GemsGuiders](
            P1Config(
              calcGuiderConfig(
                calcGuiderInUse(gc.tcsGuide, TipTiltSource.PWFS1, M1Source.PWFS1) | aog.isP1Used,
                config.guideWithP1
              )
            ),
            GemsGuiders(
              CWFS1Config(
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithCWFS1
                )
              ),
              CWFS2Config(
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithCWFS2
                )
              ),
              CWFS3Config(
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithCWFS3
                )
              ),
              ODGW1Config(
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithODGW1
                )
              ),
              ODGW2Config(
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithODGW2
                )
              ),
              ODGW3Config(
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithODGW3
                )
              ),
              ODGW4Config(
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithODGW4
                )
              )
            ),
            OIConfig(
              calcGuiderConfig(
                calcGuiderInUse(gc.tcsGuide, TipTiltSource.OIWFS, M1Source.OIWFS) | aog.isOIUsed,
                config.guideWithOI
              )
            )
          ),
          AGConfig(config.lightPath, HrwfsConfig.Auto.some),
          aog,
          config.instrument
        ): TcsSouthConfig
      }

  def buildTcsConfig: F[TcsSouthConfig] =
    guideDb.value.flatMap { c =>
      if (gaos.isDefined) buildTcsAoConfig(c.config)
      else buildBasicTcsConfig(c.config)
    }

}

object TcsSouth {

  import Tcs.*

  final case class TcsSeqConfig[F[_]](
    guideWithP1:    Option[StepGuideState],
    guideWithP2:    Option[StepGuideState],
    guideWithOI:    Option[StepGuideState],
    guideWithCWFS1: Option[StepGuideState],
    guideWithCWFS2: Option[StepGuideState],
    guideWithCWFS3: Option[StepGuideState],
    guideWithODGW1: Option[StepGuideState],
    guideWithODGW2: Option[StepGuideState],
    guideWithODGW3: Option[StepGuideState],
    guideWithODGW4: Option[StepGuideState],
    offsetA:        Option[InstrumentOffset],
    wavelA:         Option[Wavelength],
    lightPath:      LightPath,
    instrument:     InstrumentGuide
  )

  def fromConfig[F[_]: Sync: Logger](
    controller:          TcsSouthController[F],
    subsystems:          NonEmptySet[Subsystem],
    gaos:                Option[Gems[F]],
    instrument:          InstrumentGuide,
    guideConfigDb:       GuideConfigDb[F]
  )(
    targets:             TargetEnvironment,
    telescopeConfig:     CoreTelescopeConfig,
    lightPath:           LightPath,
    observingWavelength: Option[Wavelength]
  ): TcsSouth[F] = {
    val p: Offset.P = telescopeConfig.offset.p
    val q: Offset.Q = telescopeConfig.offset.q

    val guiding: StepGuideState = telescopeConfig.guiding

    val gwp1   = none.map(_ => guiding)
    val gwp2   = none.map(_ => guiding)
    val gwoi   = none.map(_ => guiding)
    val gwc1   = none.map(_ => guiding)
    val gwc2   = none.map(_ => guiding)
    val gwc3   = none.map(_ => guiding)
    val gwod1  = none.map(_ => guiding)
    val gwod2  = none.map(_ => guiding)
    val gwod3  = none.map(_ => guiding)
    val gwod4  = none.map(_ => guiding)
    val offset =
      InstrumentOffset(
        OffsetP(Angle.signedDecimalArcseconds.get(p.toAngle).toDouble.withUnit[ArcSecond]),
        OffsetQ(Angle.signedDecimalArcseconds.get(q.toAngle).toDouble.withUnit[ArcSecond])
      ).some

    val tcsSeqCfg = TcsSeqConfig[F](
      gwp1,
      gwp2,
      gwoi,
      gwc1,
      gwc2,
      gwc3,
      gwod1,
      gwod2,
      gwod3,
      gwod4,
      offset,
      observingWavelength,
      lightPath,
      instrument
    )

    new TcsSouth(controller, subsystems, gaos, guideConfigDb)(tcsSeqCfg)

  }

}
