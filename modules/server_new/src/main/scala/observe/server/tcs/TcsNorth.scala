// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.data.NonEmptySet
import cats.effect.Sync
import cats.syntax.all.*
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import lucuma.core.enums.M1Source
import lucuma.core.enums.Site
import lucuma.core.enums.StepGuideState
import lucuma.core.enums.TipTiltSource
import lucuma.core.math.Angle
import lucuma.core.math.Offset
import lucuma.core.math.Wavelength
import lucuma.core.model.GuideConfig
import lucuma.core.model.sequence.TelescopeConfig as CoreTelescopeConfig
import mouse.all.*
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment
import observe.model.enums.NodAndShuffleStage
import observe.model.enums.Resource
import observe.server.ConfigResult
import observe.server.InstrumentGuide
import observe.server.ObserveFailure
import observe.server.altair.Altair
import observe.server.tcs.TcsController.*
import observe.server.tcs.TcsNorthController.TcsNorthAoConfig
import observe.server.tcs.TcsNorthController.TcsNorthConfig
import org.typelevel.log4cats.Logger

class TcsNorth[F[_]: Sync: Logger] private (
  tcsController: TcsNorthController[F],
  subsystems:    NonEmptySet[Subsystem],
  gaos:          Option[Altair[F]],
  guideDb:       GuideConfigDb[F]
)(config: TcsNorth.TcsSeqConfig[F])
    extends Tcs[F] {
  import Tcs.*

  val Log: Logger[F] = Logger[F]

  override val resource: Resource = Resource.TCS

  // Helper function to output the part of the TCS configuration that is actually applied.
  private def subsystemConfig(tcs: TcsNorthConfig, subsystem: Subsystem): String =
    (subsystem match {
      case Subsystem.M1     => pprint.apply(tcs.gc.m1Guide)
      case Subsystem.M2     => pprint.apply(tcs.gc.m2Guide)
      case Subsystem.OIWFS  => pprint.apply(tcs.gds.oiwfs.value)
      case Subsystem.PWFS1  => pprint.apply(tcs.gds.pwfs1.value)
      case Subsystem.PWFS2  => pprint.apply(tcs.gds.pwfs2.value)
      case Subsystem.Mount  => pprint.apply(tcs.tc)
      case Subsystem.AGUnit => pprint.apply(List(tcs.agc.sfPos, tcs.agc.hrwfs))
      case Subsystem.Gaos   =>
        tcs match {
          case x: TcsNorthAoConfig => pprint.apply(x.gds.aoguide)
          case _                   => pprint.apply("")
        }
    }).plainText

  override def configure: F[ConfigResult[F]] =
    buildTcsConfig.flatMap { cfg =>
      subsystems.traverse_(s =>
        Log.debug(s"Applying TCS/$s configuration/config: ${subsystemConfig(cfg, s)}")
      ) *>
        tcsController.applyConfig(subsystems, gaos, cfg).as(ConfigResult(this))
    }

  override def notifyObserveStart: F[Unit] = tcsController.notifyObserveStart

  override def notifyObserveEnd: F[Unit] = tcsController.notifyObserveEnd

  val defaultGuiderConf: GuiderConfig = GuiderConfig(ProbeTrackingConfig.Parked, GuiderSensorOff)
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
  private def buildBasicTcsConfig(gc: GuideConfig): F[TcsNorthConfig] =
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
    ): TcsNorthConfig).pure[F]

  private def buildTcsAoConfig(gc: GuideConfig, ao: Altair[F]): F[TcsNorthConfig] =
    gc.gaosGuide
      .flatMap(_.swap.toOption.map { aog =>
        val aoGuiderConfig = ao
          .hasTarget(aog)
          .fold(
            calcGuiderConfig(calcGuiderInUse(gc.tcsGuide, TipTiltSource.GAOS, M1Source.GAOS),
                             config.guideWithAO
            ),
            GuiderConfig(ProbeTrackingConfig.Off,
                         config.guideWithAO.map(_.toGuideSensorOption).getOrElse(GuiderSensorOff)
            )
          )

        AoTcsConfig[Site.GN.type](
          gc.tcsGuide,
          TelescopeConfig(config.offsetA, config.wavelA),
          AoGuidersConfig[AoGuide](
            P1Config(
              calcGuiderConfig(
                calcGuiderInUse(gc.tcsGuide, TipTiltSource.PWFS1, M1Source.PWFS1) | ao.usesP1(aog),
                config.guideWithP1
              )
            ),
            AoGuide(aoGuiderConfig),
            OIConfig(
              calcGuiderConfig(
                calcGuiderInUse(gc.tcsGuide, TipTiltSource.OIWFS, M1Source.OIWFS) | ao.usesOI(aog),
                config.guideWithOI
              )
            )
          ),
          AGConfig(config.lightPath, HrwfsConfig.Auto.some),
          aog,
          config.instrument
        ): TcsNorthConfig
      })
      .map(_.pure[F])
      .getOrElse(
        ObserveFailure
          .Execution("Attempting to run Altair sequence before Altair has being configured.")
          .raiseError[F, TcsNorthConfig]
      )

  def buildTcsConfig: F[TcsNorthConfig] =
    guideDb.value.flatMap { c =>
      gaos
        .map(buildTcsAoConfig(c.config, _))
        .getOrElse(buildBasicTcsConfig(c.config))
    }

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
}

object TcsNorth {

  import Tcs.*

  final case class TcsSeqConfig[F[_]](
    guideWithP1: Option[StepGuideState],
    guideWithP2: Option[StepGuideState],
    guideWithOI: Option[StepGuideState],
    guideWithAO: Option[StepGuideState],
    offsetA:     Option[InstrumentOffset],
    wavelA:      Option[Wavelength],
    lightPath:   LightPath,
    instrument:  InstrumentGuide
  )

  def fromConfig[F[_]: Sync: Logger](
    controller:          TcsNorthController[F],
    subsystems:          NonEmptySet[Subsystem],
    gaos:                Option[Altair[F]],
    instrument:          InstrumentGuide,
    guideConfigDb:       GuideConfigDb[F]
  )(
    targets:             TargetEnvironment,
    telescopeConfig:     CoreTelescopeConfig,
    lightPath:           LightPath,
    observingWavelength: Option[Wavelength]
  ): TcsNorth[F] = {
    val p: Offset.P = telescopeConfig.offset.p
    val q: Offset.Q = telescopeConfig.offset.q

    val guiding: StepGuideState = telescopeConfig.guiding

    val gwp1   = none.map(_ => guiding)
    val gwp2   = none.map(_ => guiding)
    val gwoi   = none.map(_ => guiding)
    val gwao   = none.map(_ => guiding)
    val offset =
      InstrumentOffset(
        OffsetP(Angle.signedDecimalArcseconds.get(p.toAngle).toDouble.withUnit[ArcSecond]),
        OffsetQ(Angle.signedDecimalArcseconds.get(q.toAngle).toDouble.withUnit[ArcSecond])
      ).some

    val tcsSeqCfg = TcsSeqConfig[F](
      gwp1,
      gwp2,
      gwoi,
      gwao,
      offset,
      observingWavelength,
      lightPath,
      instrument
    )

    new TcsNorth(controller, subsystems, gaos, guideConfigDb)(tcsSeqCfg)

  }

}
