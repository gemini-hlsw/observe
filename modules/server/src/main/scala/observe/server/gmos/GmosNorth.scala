// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import lucuma.core.enums.LightSinkName
import observe.model.enums.Instrument
import observe.server.InstrumentSpecifics
import observe.server.ObserveFailure
import observe.server.StepType
import observe.server.gmos.Gmos.SiteSpecifics
import observe.server.gmos.GmosController.northConfigTypes
import observe.server.keywords.DhsClient
import observe.server.tcs.FOCAL_PLANE_SCALE
import squants.Length
import squants.space.Arcseconds
import cats.effect.{Ref, Temporal}
import edu.gemini.spModel.gemini.ghost.Ghost.INSTRUMENT_NAME_PROP
import observe.common.ObsQueriesGQL.ObsQuery.{GmosInstrumentConfig, GmosSite, GmosStatic}

final case class GmosNorth[F[_]: Temporal: Logger] private (
  c:          GmosNorthController[F],
  dhsClient:  DhsClient[F],
  nsCmdR:     Ref[F, Option[NSObserveCommand]],
  obsType:    StepType,
  staticCfg:  GmosStatic[GmosSite.North],
  dynamicCfg: GmosInstrumentConfig[GmosSite.North]
) extends Gmos[F, GmosSite.North](
      c,
      new SiteSpecifics[GmosSite.North] {
        def extractFilter(
          d: GmosInstrumentConfig[GmosSite.North]
        ): Option[GmosSite.North#Filter] = d.filter

        def extractDisperser(
          d: GmosInstrumentConfig[GmosSite.North]
        ): Option[GmosSite.North#Grating] = d.gratingConfig.map(_.grating)

        def extractFPU(
          d: GmosInstrumentConfig[GmosSite.North]
        ): Option[GmosSite.North#BuiltInFpu] = d.fpu.flatMap(_.builtin)

        def extractStageMode(
          s: GmosStatic[GmosSite.North]
        ): GmosSite.North#StageMode = s.stageMode
      },
      nsCmdR,
      obsType,
      staticCfg,
      dynamicCfg
    )(
      northConfigTypes
    ) {
  override val resource: Instrument      = Instrument.GmosN
  override val dhsInstrumentName: String = "GMOS-N"

}

object GmosNorth {
  val name: String = INSTRUMENT_NAME_PROP

  def apply[F[_]: Temporal: Logger](
    c:          GmosController[F, GmosSite.North],
    dhsClient:  DhsClient[F],
    nsCmdR:     Ref[F, Option[NSObserveCommand]],
    obsType:    StepType,
    staticCfg:  GmosStatic[GmosSite.North],
    dynamicCfg: GmosInstrumentConfig[GmosSite.North]
  ): GmosNorth[F] = new GmosNorth[F](c, dhsClient, nsCmdR, obsType, staticCfg, dynamicCfg)

  object specifics extends InstrumentSpecifics {
    override val instrument: Instrument = Instrument.GmosN

    override def sfName(config: CleanConfig): LightSinkName = LightSinkName.Gmos

    // TODO Use different value if using electronic offsets
    override val oiOffsetGuideThreshold: Option[Length] =
      (Arcseconds(0.01) / FOCAL_PLANE_SCALE).some

  }

}
