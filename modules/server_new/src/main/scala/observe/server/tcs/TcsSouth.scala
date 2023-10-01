// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.data.NonEmptySet
import cats.effect.Sync
import cats.syntax.all.*
import coulomb.*
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import lucuma.core.enums.GuideState
import lucuma.core.enums.Site
import lucuma.core.math.Angle
import lucuma.core.math.Wavelength
import lucuma.core.model.sequence.StepConfig
import mouse.all.*
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment
import observe.model.enums.M1Source
import observe.model.enums.NodAndShuffleStage
import observe.model.enums.Resource
import observe.model.enums.TipTiltSource
import observe.server.ConfigResult
import observe.server.InstrumentGuide
import observe.server.ObserveFailure
import observe.server.gems.Gems
import observe.server.gems.GemsController.GemsConfig
import observe.server.gems.GemsController.GemsOff
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
    guideWith: Option[GuideState]
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
    gc.guideWithCWFS1.exists(_ === GuideState.Enabled) ||
      gc.guideWithCWFS2.exists(_ === GuideState.Enabled) ||
      gc.guideWithCWFS3.exists(_ === GuideState.Enabled) ||
      gc.guideWithODGW1.exists(_ === GuideState.Enabled) ||
      gc.guideWithODGW2.exists(_ === GuideState.Enabled) ||
      gc.guideWithODGW3.exists(_ === GuideState.Enabled) ||
      gc.guideWithODGW4.exists(_ === GuideState.Enabled)

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
      if (gaos.isDefined) buildTcsAoConfig(c)
      else buildBasicTcsConfig(c)
    }

}

object TcsSouth {

  import Tcs.*

  final case class TcsSeqConfig[F[_]](
    guideWithP1:    Option[GuideState],
    guideWithP2:    Option[GuideState],
    guideWithOI:    Option[GuideState],
    guideWithCWFS1: Option[GuideState],
    guideWithCWFS2: Option[GuideState],
    guideWithCWFS3: Option[GuideState],
    guideWithODGW1: Option[GuideState],
    guideWithODGW2: Option[GuideState],
    guideWithODGW3: Option[GuideState],
    guideWithODGW4: Option[GuideState],
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
    stepConfig:          StepConfig,
    lightPath:           LightPath,
    observingWavelength: Option[Wavelength]
  ): TcsSouth[F] = {

    val gwp1   = none.flatMap(_ =>
      StepConfig.science.andThen(StepConfig.Science.guiding).getOption(stepConfig)
    )
    val gwp2   = none.flatMap(_ =>
      StepConfig.science.andThen(StepConfig.Science.guiding).getOption(stepConfig)
    )
    val gwoi   = none.flatMap(_ =>
      StepConfig.science.andThen(StepConfig.Science.guiding).getOption(stepConfig)
    )
    val gwc1   = none.flatMap(_ =>
      StepConfig.science.andThen(StepConfig.Science.guiding).getOption(stepConfig)
    )
    val gwc2   = none.flatMap(_ =>
      StepConfig.science.andThen(StepConfig.Science.guiding).getOption(stepConfig)
    )
    val gwc3   = none.flatMap(_ =>
      StepConfig.science.andThen(StepConfig.Science.guiding).getOption(stepConfig)
    )
    val gwod1  = none.flatMap(_ =>
      StepConfig.science.andThen(StepConfig.Science.guiding).getOption(stepConfig)
    )
    val gwod2  = none.flatMap(_ =>
      StepConfig.science.andThen(StepConfig.Science.guiding).getOption(stepConfig)
    )
    val gwod3  = none.flatMap(_ =>
      StepConfig.science.andThen(StepConfig.Science.guiding).getOption(stepConfig)
    )
    val gwod4  = none.flatMap(_ =>
      StepConfig.science.andThen(StepConfig.Science.guiding).getOption(stepConfig)
    )
    val offset =
      StepConfig.science.andThen(StepConfig.Science.offset).getOption(stepConfig).map { o =>
        InstrumentOffset(
          OffsetP(Angle.signedDecimalArcseconds.get(o.p.toAngle).toDouble.withUnit[ArcSecond]),
          OffsetQ(Angle.signedDecimalArcseconds.get(o.q.toAngle).toDouble.withUnit[ArcSecond])
        )
      }

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
