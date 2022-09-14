// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.data.NonEmptySet
import cats.effect.Sync
import cats.syntax.all._
import lucuma.core.math.Wavelength
//import edu.gemini.spModel.gemini.gems.CanopusWfs
//import edu.gemini.spModel.gemini.gsaoi.GsaoiOdgw
//import edu.gemini.spModel.guide.StandardGuideOptions
import org.typelevel.log4cats.Logger
import monocle.macros.Lenses
import mouse.all._
import observe.model.enums.M1Source
import observe.model.enums.NodAndShuffleStage
import observe.model.enums.Resource
import observe.model.enums.TipTiltSource
import observe.server.CleanConfig
import observe.server.CleanConfig.extractItem
import observe.server.ConfigResult
import observe.server.ConfigUtilOps._
import observe.server.InstrumentGuide
import observe.server.ObserveFailure
import observe.server.gems.Gems
import observe.server.gems.GemsController.{GemsConfig, GemsOff}
import observe.server.tcs.TcsController.AGConfig
import observe.server.tcs.TcsController.AoGuidersConfig
import observe.server.tcs.TcsController.AoTcsConfig
import observe.server.tcs.TcsController.BasicGuidersConfig
import observe.server.tcs.TcsController.BasicTcsConfig
import observe.server.tcs.TcsController.GuiderConfig
import observe.server.tcs.TcsController.GuiderSensorOff
import observe.server.tcs.TcsController.HrwfsConfig
import observe.server.tcs.TcsController.InstrumentOffset
import observe.server.tcs.TcsController.LightPath
import observe.server.tcs.TcsController.OIConfig
import observe.server.tcs.TcsController.OffsetP
import observe.server.tcs.TcsController.OffsetQ
import observe.server.tcs.TcsController.P1Config
import observe.server.tcs.TcsController.P2Config
import observe.server.tcs.TcsController.ProbeTrackingConfig
import observe.server.tcs.TcsController.Subsystem
import observe.server.tcs.TcsController.TelescopeConfig
import observe.server.tcs.TcsSouthController.CWFS1Config
import observe.server.tcs.TcsSouthController.CWFS2Config
import observe.server.tcs.TcsSouthController.CWFS3Config
import observe.server.tcs.TcsSouthController.GemsGuiders
import observe.server.tcs.TcsSouthController.ODGW1Config
import observe.server.tcs.TcsSouthController.ODGW2Config
import observe.server.tcs.TcsSouthController.ODGW3Config
import observe.server.tcs.TcsSouthController.ODGW4Config
import observe.server.tcs.TcsSouthController.TcsSouthConfig
import shapeless.tag

case class TcsSouth[F[_]: Sync: Logger] private (
  tcsController: TcsSouthController[F],
  subsystems:    NonEmptySet[Subsystem],
  gaos:          Option[Gems[F]],
  guideDb:       GuideConfigDb[F]
)(config:        TcsSouth.TcsSeqConfig[F])
    extends Tcs[F] {
  import Tcs.{GuideWithOps, calcGuiderInUse}

  val Log: Logger[F] = Logger[F]

  override val resource: Resource = Resource.TCS

  override def configure(config: CleanConfig): F[ConfigResult[F]] =
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
    guideWith: Option[StandardGuideOptions.Value]
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
        tag[P1Config](
          calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.PWFS1, M1Source.PWFS1),
                           config.guideWithP1
          )
        ),
        tag[P2Config](
          calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.PWFS2, M1Source.PWFS2),
                           config.guideWithP2
          )
        ),
        tag[OIConfig](
          calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.OIWFS, M1Source.OIWFS),
                           config.guideWithOI
          )
        )
      ),
      AGConfig(config.lightPath, HrwfsConfig.Auto.some),
      config.instrument
    ): TcsSouthConfig).pure[F]

  private def anyGeMSGuiderActive(gc: TcsSouth.TcsSeqConfig[F]): Boolean =
    gc.guideWithCWFS1.exists(_.isActive) ||
      gc.guideWithCWFS2.exists(_.isActive) ||
      gc.guideWithCWFS3.exists(_.isActive) ||
      gc.guideWithODGW1.exists(_.isActive) ||
      gc.guideWithODGW2.exists(_.isActive) ||
      gc.guideWithODGW3.exists(_.isActive) ||
      gc.guideWithODGW4.exists(_.isActive)

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
        AoTcsConfig[GemsGuiders, GemsConfig](
          gc.tcsGuide,
          TelescopeConfig(config.offsetA, config.wavelA),
          AoGuidersConfig[GemsGuiders](
            tag[P1Config](
              calcGuiderConfig(
                calcGuiderInUse(gc.tcsGuide, TipTiltSource.PWFS1, M1Source.PWFS1) | aog.isP1Used,
                config.guideWithP1
              )
            ),
            GemsGuiders(
              tag[CWFS1Config](
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithCWFS1
                )
              ),
              tag[CWFS2Config](
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithCWFS2
                )
              ),
              tag[CWFS3Config](
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithCWFS3
                )
              ),
              tag[ODGW1Config](
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithODGW1
                )
              ),
              tag[ODGW2Config](
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithODGW2
                )
              ),
              tag[ODGW3Config](
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithODGW3
                )
              ),
              tag[ODGW4Config](
                calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                                 config.guideWithODGW4
                )
              )
            ),
            tag[OIConfig](
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

  import Tcs._

  @Lenses
  final case class TcsSeqConfig[F[_]](
    guideWithP1:    Option[StandardGuideOptions.Value],
    guideWithP2:    Option[StandardGuideOptions.Value],
    guideWithOI:    Option[StandardGuideOptions.Value],
    guideWithCWFS1: Option[StandardGuideOptions.Value],
    guideWithCWFS2: Option[StandardGuideOptions.Value],
    guideWithCWFS3: Option[StandardGuideOptions.Value],
    guideWithODGW1: Option[StandardGuideOptions.Value],
    guideWithODGW2: Option[StandardGuideOptions.Value],
    guideWithODGW3: Option[StandardGuideOptions.Value],
    guideWithODGW4: Option[StandardGuideOptions.Value],
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
    config:              CleanConfig,
    lightPath:           LightPath,
    observingWavelength: Option[Wavelength]
  ): TcsSouth[F] = {

    val gwp1    = config.extractTelescopeAs[StandardGuideOptions.Value](GUIDE_WITH_PWFS1_PROP).toOption
    val gwp2    = config.extractTelescopeAs[StandardGuideOptions.Value](GUIDE_WITH_PWFS2_PROP).toOption
    val gwoi    = config.extractTelescopeAs[StandardGuideOptions.Value](GUIDE_WITH_OIWFS_PROP).toOption
    val gwc1    = config
      .extractTelescopeAs[StandardGuideOptions.Value](CanopusWfs.cwfs1.getSequenceProp)
      .toOption
    val gwc2    = config
      .extractTelescopeAs[StandardGuideOptions.Value](CanopusWfs.cwfs2.getSequenceProp)
      .toOption
    val gwc3    = config
      .extractTelescopeAs[StandardGuideOptions.Value](CanopusWfs.cwfs3.getSequenceProp)
      .toOption
    val gwod1   = config
      .extractTelescopeAs[StandardGuideOptions.Value](GsaoiOdgw.odgw1.getSequenceProp)
      .toOption
    val gwod2   = config
      .extractTelescopeAs[StandardGuideOptions.Value](GsaoiOdgw.odgw2.getSequenceProp)
      .toOption
    val gwod3   = config
      .extractTelescopeAs[StandardGuideOptions.Value](GsaoiOdgw.odgw3.getSequenceProp)
      .toOption
    val gwod4   = config
      .extractTelescopeAs[StandardGuideOptions.Value](GsaoiOdgw.odgw4.getSequenceProp)
      .toOption
    val offsetp = config
      .extractTelescopeAs[String](P_OFFSET_PROP)
      .toOption
      .flatMap(_.parseDoubleOption)
      .map(Arcseconds(_): Angle)
      .map(tag[OffsetP](_))
    val offsetq = config
      .extractTelescopeAs[String](Q_OFFSET_PROP)
      .toOption
      .flatMap(_.parseDoubleOption)
      .map(Arcseconds(_): Angle)
      .map(tag[OffsetQ](_))

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
      (offsetp, offsetq).mapN(InstrumentOffset(_, _)),
      observingWavelength,
      lightPath,
      instrument
    )

    new TcsSouth(controller, subsystems, gaos, guideConfigDb)(tcsSeqCfg)

  }

}
