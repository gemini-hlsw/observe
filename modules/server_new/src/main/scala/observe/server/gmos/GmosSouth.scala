// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.MonadThrow
import cats.effect.{Ref, Temporal}
import cats.syntax.all.*
import lucuma.core.enums.{GmosRoi, LightSinkName, MosPreImaging}
import lucuma.core.math.Wavelength
import lucuma.core.model.sequence.gmos.{DynamicConfig, GmosCcdMode, GmosNodAndShuffle, StaticConfig}
import lucuma.core.util.TimeSpan
import monocle.Getter
import observe.model.enums.Instrument
import observe.server.gmos.GmosController.Config.{DTAX, GratingOrder}
import observe.server.gmos.GmosController.GmosSite
import observe.server.gmos.GmosController.GmosSite.{FPU, Filter, Grating, StageMode}
import observe.server.keywords.{DhsClient, DhsClientProvider}
import observe.server.tcs.FOCAL_PLANE_SCALE
import observe.server.{ObserveFailure, StepType}
import org.typelevel.log4cats.Logger
import squants.Length
import squants.space.Arcseconds

final case class GmosSouth[F[_]: Temporal: Logger](
  c:                 GmosSouthController[F],
  dhsClientProvider: DhsClientProvider[F],
  nsCmdR:            Ref[F, Option[NSObserveCommand]],
  obsType:           StepType,
  cfg:               GmosController.GmosConfig[GmosSite.South.type]
) extends Gmos[F, GmosSite.South.type](
      c,
      nsCmdR,
      obsType,
      cfg
    ) {
  override val resource: Instrument      = Instrument.GmosS
  override val dhsInstrumentName: String = "GMOS-S"
  override val dhsClient: DhsClient[F]   = dhsClientProvider.dhsClient(dhsInstrumentName)

  override val instrument: Instrument = Instrument.GmosS

  override def sfName: LightSinkName = LightSinkName.Gmos

  // TODO Use different value if using electronic offsets
  override val oiOffsetGuideThreshold: Option[Length] =
    (Arcseconds(0.01) / FOCAL_PLANE_SCALE).some
}

object GmosSouth {

  given Gmos.ParamGetters[GmosSite.South.type, StaticConfig.GmosSouth, DynamicConfig.GmosSouth] =
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
    obsType:           StepType,
    staticCfg:         StaticConfig.GmosSouth,
    dynamicCfg:        DynamicConfig.GmosSouth
  ): Either[ObserveFailure, GmosSouth[F]] =
    Gmos
      .buildConfig[F, GmosSite.South.type, StaticConfig.GmosSouth, DynamicConfig.GmosSouth](
        Instrument.GmosS,
        obsType,
        staticCfg,
        dynamicCfg
      )
      .map { case (t, config) =>
        GmosSouth(controller, dhsClientProvider, nsCmdR, t, config)
      }

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
}
