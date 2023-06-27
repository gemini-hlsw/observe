// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import lucuma.core.enums.LightSinkName
import observe.model.enums.Instrument
import observe.server.CleanConfig
import observe.server.InstrumentSpecifics
import observe.server.ObserveFailure
import observe.server.StepType
import observe.server.gmos.Gmos.SiteSpecifics
import observe.server.gmos.GmosController.southConfigTypes
import observe.server.keywords.DhsClient
import observe.server.tcs.FOCAL_PLANE_SCALE
import squants.Length
import squants.space.Arcseconds
import cats.effect.{Ref, Temporal}
import observe.common.ObsQueriesGQL.ObsQuery.{GmosInstrumentConfig, GmosSite, GmosStatic}

final case class GmosSouth[F[_]: Temporal: Logger] private (
  c:          GmosSouthController[F],
  dhsClient:  DhsClient[F],
  nsCmdR:     Ref[F, Option[NSObserveCommand]],
  obsType:    StepType,
  staticCfg:  GmosStatic[GmosSite.South],
  dynamicCfg: GmosInstrumentConfig[GmosSite.South]
) extends Gmos[F, GmosSite.South](
      c,
      new SiteSpecifics[GmosSite.South] {
        def extractFilter(
          d: GmosInstrumentConfig[GmosSite.South]
        ): Option[GmosSite.South#Filter] = d.filter

        def extractDisperser(
          d: GmosInstrumentConfig[GmosSite.South]
        ): Option[GmosSite.South#Grating] = d.gratingConfig.map(_.grating)

        def extractFPU(
          d: GmosInstrumentConfig[GmosSite.South]
        ): Option[GmosSite.South#BuiltInFpu] = d.fpu.flatMap(_.builtin)

        def extractStageMode(
          s: GmosStatic[GmosSite.South]
        ): GmosSite.South#StageMode = s.stageMode
      },
      nsCmdR,
      obsType,
      staticCfg,
      dynamicCfg
    )(
      southConfigTypes
    ) {
  override val resource: Instrument      = Instrument.GmosS
  override val dhsInstrumentName: String = "GMOS-S"
}

object GmosSouth {

  def apply[F[_]: Temporal: Logger](
    c:          GmosController[F, GmosSite.South],
    dhsClient:  DhsClient[F],
    nsCmdR:     Ref[F, Option[NSObserveCommand]],
    obsType:    StepType,
    staticCfg:  GmosStatic[GmosSite.South],
    dynamicCfg: GmosInstrumentConfig[GmosSite.South]
  ): GmosSouth[F] = new GmosSouth[F](c, dhsClient, nsCmdR, obsType, staticCfg, dynamicCfg)

  object specifics extends InstrumentSpecifics {
    override val instrument: Instrument = Instrument.GmosS

    override def sfName(config: CleanConfig): LightSinkName = LightSinkName.Gmos

    // TODO Use different value if using electronic offsets
    override val oiOffsetGuideThreshold: Option[Length] =
      (Arcseconds(0.01) / FOCAL_PLANE_SCALE).some

  }

}
