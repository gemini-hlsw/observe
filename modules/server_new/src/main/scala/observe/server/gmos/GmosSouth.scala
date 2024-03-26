// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.MonadThrow
import cats.effect.Ref
import cats.effect.Temporal
import cats.syntax.all.*
import coulomb.Quantity
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import coulomb.units.accepted.Millimeter
import lucuma.core.enums.GmosRoi
import lucuma.core.enums.Instrument
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
import observe.server.tcs.*
import observe.server.tcs.FocalPlaneScale.*
import org.typelevel.log4cats.Logger

final case class GmosSouth[F[_]: Temporal: Logger](
  c:                 GmosSouthController[F],
  dhsClientProvider: DhsClientProvider[F],
  nsCmdR:            Ref[F, Option[NSObserveCommand]],
  cfg:               GmosController.GmosConfig[GmosSite.South.type]
) extends Gmos[F, GmosSite.South.type](
      c,
      nsCmdR,
      cfg
    ) {
  override val resource: Instrument      = Instrument.GmosSouth
  override val dhsInstrumentName: String = "GMOS-S"
  override val dhsClient: DhsClient[F]   = dhsClientProvider.dhsClient(dhsInstrumentName)

}

object GmosSouth {

  given gsParamGetters
    : Gmos.ParamGetters[GmosSite.South.type, StaticConfig.GmosSouth, DynamicConfig.GmosSouth] =
    new Gmos.ParamGetters[GmosSite.South.type, StaticConfig.GmosSouth, DynamicConfig.GmosSouth] {
      override val exposure: Getter[DynamicConfig.GmosSouth, TimeSpan]                            =
        DynamicConfig.GmosSouth.exposure.asGetter
      override val filter: Getter[DynamicConfig.GmosSouth, Option[Filter[GmosSite.South.type]]]   =
        DynamicConfig.GmosSouth.filter.asGetter
      override val grating: Getter[DynamicConfig.GmosSouth, Option[Grating[GmosSite.South.type]]] =
        DynamicConfig.GmosSouth.gratingConfig.asGetter.map(_.map(_.grating))
      override val order: Getter[DynamicConfig.GmosSouth, Option[GratingOrder]]                   =
        DynamicConfig.GmosSouth.gratingConfig.asGetter.map(_.map(_.order))
      override val wavelength: Getter[DynamicConfig.GmosSouth, Option[Wavelength]]                =
        DynamicConfig.GmosSouth.gratingConfig.asGetter.map(_.map(_.wavelength))
      override val builtinFpu: Getter[DynamicConfig.GmosSouth, Option[FPU[GmosSite.South.type]]]  =
        DynamicConfig.GmosSouth.fpu.asGetter.map(_.flatMap(_.builtinFpu))
      override val customFpu: Getter[DynamicConfig.GmosSouth, Option[String]]                     =
        DynamicConfig.GmosSouth.fpu.asGetter.map(_.flatMap(_.customFilename.map(_.toString)))
      override val dtax: Getter[DynamicConfig.GmosSouth, DTAX]                                    =
        DynamicConfig.GmosSouth.dtax.asGetter
      override val stageMode: Getter[StaticConfig.GmosSouth, StageMode[GmosSite.South.type]]      =
        StaticConfig.GmosSouth.stageMode.asGetter
      override val nodAndShuffle: Getter[StaticConfig.GmosSouth, Option[GmosNodAndShuffle]]       =
        StaticConfig.GmosSouth.nodAndShuffle.asGetter
      override val roi: Getter[DynamicConfig.GmosSouth, GmosRoi]                                  =
        DynamicConfig.GmosSouth.roi.asGetter
      override val readout: Getter[DynamicConfig.GmosSouth, GmosCcdMode]                          =
        DynamicConfig.GmosSouth.readout.asGetter
      override val isMosPreimaging: Getter[StaticConfig.GmosSouth, MosPreImaging]                 =
        StaticConfig.GmosSouth.mosPreImaging.asGetter
    }

  def build[F[_]: Temporal: Logger](
    controller:        GmosController[F, GmosSite.South.type],
    dhsClientProvider: DhsClientProvider[F],
    nsCmdR:            Ref[F, Option[NSObserveCommand]],
    stepType:          StepType,
    staticCfg:         StaticConfig.GmosSouth,
    dynamicCfg:        DynamicConfig.GmosSouth
  ): GmosSouth[F] = GmosSouth(
    controller,
    dhsClientProvider,
    nsCmdR,
    Gmos.buildConfig[F, GmosSite.South.type, StaticConfig.GmosSouth, DynamicConfig.GmosSouth](
      Instrument.GmosSouth,
      stepType,
      staticCfg,
      dynamicCfg
    )
  )

  def obsKeywordsReader[F[_]: MonadThrow](
    staticConfig:  StaticConfig.GmosSouth,
    dynamicConfig: DynamicConfig.GmosSouth
  )(using
    getters:       Gmos.ParamGetters[GmosSite.South.type, StaticConfig.GmosSouth, DynamicConfig.GmosSouth]
  ): GmosObsKeywordsReader[F,
                           GmosSite.South.type,
                           StaticConfig.GmosSouth,
                           DynamicConfig.GmosSouth
  ] =
    GmosObsKeywordsReader(staticConfig, dynamicConfig)

  object specifics extends InstrumentSpecifics[StaticConfig.GmosSouth, DynamicConfig.GmosSouth] {
    override val instrument: Instrument = Instrument.GmosSouth

    override def calcStepType(
      stepConfig:   StepConfig,
      staticConfig: StaticConfig.GmosSouth,
      instConfig:   DynamicConfig.GmosSouth,
      obsClass:     ObserveClass
    ): Either[ObserveFailure, StepType] =
      Gmos.calcStepType(instrument,
                        stepConfig,
                        staticConfig,
                        obsClass,
                        gsParamGetters.nodAndShuffle
      )

    override def sfName(config: DynamicConfig.GmosSouth): LightSinkName = LightSinkName.Gmos

    // TODO Use different value if using electronic offsets
    override val oiOffsetGuideThreshold: Option[Quantity[Double, Millimeter]] =
      (0.01.withUnit[ArcSecond] :\ FOCAL_PLANE_SCALE).some
  }

}
