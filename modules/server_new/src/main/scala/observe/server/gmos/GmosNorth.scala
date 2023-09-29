// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.MonadThrow
import cats.effect.Ref
import cats.effect.Temporal
import cats.syntax.all.*
import lucuma.core.enums.GmosRoi
import lucuma.core.enums.LightSinkName
import lucuma.core.enums.MosPreImaging
import lucuma.core.enums.ObserveClass
import lucuma.core.math.Wavelength
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.GmosCcdMode
import lucuma.core.model.sequence.gmos.GmosNodAndShuffle
import lucuma.core.model.sequence.gmos.StaticConfig
import lucuma.core.util.TimeSpan
import monocle.Getter
import observe.model.enums.Instrument
import observe.server.InstrumentSpecifics
import observe.server.ObserveFailure
import observe.server.StepType
import observe.server.gmos.GmosController.Config.DTAX
import observe.server.gmos.GmosController.Config.GratingOrder
import observe.server.gmos.GmosController.GmosSite
import observe.server.gmos.GmosController.GmosSite.FPU
import observe.server.gmos.GmosController.GmosSite.Filter
import observe.server.gmos.GmosController.GmosSite.Grating
import observe.server.gmos.GmosController.GmosSite.StageMode
import observe.server.keywords.DhsClient
import observe.server.keywords.DhsClientProvider
import observe.server.tcs.FOCAL_PLANE_SCALE
import org.typelevel.log4cats.Logger
import squants.Length
import squants.space.Arcseconds

final case class GmosNorth[F[_]: Temporal: Logger] private (
  c:                 GmosNorthController[F],
  dhsClientProvider: DhsClientProvider[F],
  nsCmdR:            Ref[F, Option[NSObserveCommand]],
  cfg:               GmosController.GmosConfig[GmosSite.North.type]
) extends Gmos[F, GmosSite.North.type](
      c,
      nsCmdR,
      cfg
    ) {
  override val resource: Instrument      = Instrument.GmosN
  override val dhsInstrumentName: String = "GMOS-N"
  override val dhsClient: DhsClient[F]   = dhsClientProvider.dhsClient(dhsInstrumentName)

}

object GmosNorth {

  given gnParamGetters
    : Gmos.ParamGetters[GmosSite.North.type, StaticConfig.GmosNorth, DynamicConfig.GmosNorth] =
    new Gmos.ParamGetters[GmosSite.North.type, StaticConfig.GmosNorth, DynamicConfig.GmosNorth] {
      override val exposure: Getter[DynamicConfig.GmosNorth, TimeSpan]                            =
        DynamicConfig.GmosNorth.exposure.asGetter
      override val filter: Getter[DynamicConfig.GmosNorth, Option[Filter[GmosSite.North.type]]]   =
        DynamicConfig.GmosNorth.filter.asGetter
      override val grating: Getter[DynamicConfig.GmosNorth, Option[Grating[GmosSite.North.type]]] =
        DynamicConfig.GmosNorth.gratingConfig.asGetter.map(_.map(_.grating))
      override val order: Getter[DynamicConfig.GmosNorth, Option[GratingOrder]]                   =
        DynamicConfig.GmosNorth.gratingConfig.asGetter.map(_.map(_.order))
      override val wavelength: Getter[DynamicConfig.GmosNorth, Option[Wavelength]]                =
        DynamicConfig.GmosNorth.gratingConfig.asGetter.map(_.map(_.wavelength))
      override val builtinFpu: Getter[DynamicConfig.GmosNorth, Option[FPU[GmosSite.North.type]]]  =
        DynamicConfig.GmosNorth.fpu.asGetter.map(_.flatMap(_.builtinFpu))
      override val customFpu: Getter[DynamicConfig.GmosNorth, Option[String]]                     =
        DynamicConfig.GmosNorth.fpu.asGetter.map(_.flatMap(_.customFilename.map(_.toString)))
      override val dtax: Getter[DynamicConfig.GmosNorth, DTAX]                                    =
        DynamicConfig.GmosNorth.dtax.asGetter
      override val stageMode: Getter[StaticConfig.GmosNorth, StageMode[GmosSite.North.type]]      =
        StaticConfig.GmosNorth.stageMode.asGetter
      override val nodAndShuffle: Getter[StaticConfig.GmosNorth, Option[GmosNodAndShuffle]]       =
        StaticConfig.GmosNorth.nodAndShuffle.asGetter
      override val roi: Getter[DynamicConfig.GmosNorth, GmosRoi]                                  =
        DynamicConfig.GmosNorth.roi.asGetter
      override val readout: Getter[DynamicConfig.GmosNorth, GmosCcdMode]                          =
        DynamicConfig.GmosNorth.readout.asGetter
      override val isMosPreimaging: Getter[StaticConfig.GmosNorth, MosPreImaging]                 =
        StaticConfig.GmosNorth.mosPreImaging.asGetter
    }

  def build[F[_]: Temporal: Logger](
    controller:        GmosController[F, GmosSite.North.type],
    dhsClientProvider: DhsClientProvider[F],
    nsCmdR:            Ref[F, Option[NSObserveCommand]],
    stepType:          StepType,
    staticCfg:         StaticConfig.GmosNorth,
    dynamicCfg:        DynamicConfig.GmosNorth
  ): GmosNorth[F] = GmosNorth(
    controller,
    dhsClientProvider,
    nsCmdR,
    Gmos.buildConfig[F, GmosSite.North.type, StaticConfig.GmosNorth, DynamicConfig.GmosNorth](
      Instrument.GmosS,
      stepType,
      staticCfg,
      dynamicCfg
    )
  )

  def obsKeywordsReader[F[_]: MonadThrow](
    staticConfig:  StaticConfig.GmosNorth,
    dynamicConfig: DynamicConfig.GmosNorth
  )(using
    getters:       Gmos.ParamGetters[GmosSite.North.type, StaticConfig.GmosNorth, DynamicConfig.GmosNorth]
  ): GmosObsKeywordsReader[F,
                           GmosSite.North.type,
                           StaticConfig.GmosNorth,
                           DynamicConfig.GmosNorth
  ] =
    GmosObsKeywordsReader(staticConfig, dynamicConfig)

  object specifics extends InstrumentSpecifics[StaticConfig.GmosNorth, DynamicConfig.GmosNorth] {
    override val instrument: Instrument = Instrument.GmosN

    override def calcStepType(
      stepConfig:   StepConfig,
      staticConfig: StaticConfig.GmosNorth,
      instConfig:   DynamicConfig.GmosNorth,
      obsClass:     ObserveClass
    ): Either[ObserveFailure, StepType] =
      Gmos.calcStepType(instrument,
                        stepConfig,
                        staticConfig,
                        obsClass,
                        gnParamGetters.nodAndShuffle
      )

    override def sfName(config: DynamicConfig.GmosNorth): LightSinkName = LightSinkName.Gmos

    // TODO Use different value if using electronic offsets
    override val oiOffsetGuideThreshold: Option[Length] =
      (Arcseconds(0.01) / FOCAL_PLANE_SCALE).some
  }
}
