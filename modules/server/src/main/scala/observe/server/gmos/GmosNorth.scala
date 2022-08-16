// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.syntax.all._
import edu.gemini.spModel.gemini.gmos.InstGmosCommon.FPU_PROP_NAME
import edu.gemini.spModel.gemini.gmos.InstGmosCommon.STAGE_MODE_PROP
import edu.gemini.spModel.gemini.gmos.InstGmosNorth.{ DISPERSER_PROP, FILTER_PROP }
import org.typelevel.log4cats.Logger
import lucuma.core.enums.LightSinkName
import observe.model.enum.Instrument
import observe.server.CleanConfig
import observe.server.CleanConfig.extractItem
import observe.server.ConfigUtilOps
import observe.server.ConfigUtilOps._
import observe.server.InstrumentSpecifics
import observe.server.ObserveFailure
import observe.server.StepType
import observe.server.gmos.Gmos.SiteSpecifics
import observe.server.gmos.GmosController.NorthTypes
import observe.server.gmos.GmosController.northConfigTypes
import observe.server.keywords.DhsClient
import observe.server.tcs.FOCAL_PLANE_SCALE
import squants.Length
import squants.space.Arcseconds
import cats.effect.{Ref, Temporal}

final case class GmosNorth[F[_]: Temporal: Logger] private (
  c:         GmosNorthController[F],
  dhsClient: DhsClient[F],
  nsCmdR:    Ref[F, Option[NSObserveCommand]]
) extends Gmos[F, NorthTypes](
      c,
      new SiteSpecifics[NorthTypes] {
        def extractFilter(
          config: CleanConfig
        ): Either[ExtractFailure, Option[NorthTypes#Filter]] =
          config.extractInstAs[NorthTypes#Filter](FILTER_PROP) match {
            case Left(KeyNotFound(_)) => none.asRight
            case Right(value)         => value.some.asRight
            case Left(e)              => e.asLeft
          }

        def extractDisperser(
          config: CleanConfig
        ): Either[ConfigUtilOps.ExtractFailure, Option[NorthTypes#Grating]] =
          config.extractInstAs[NorthTypes#Grating](DISPERSER_PROP) match {
            case Left(KeyNotFound(_)) => none.asRight
            case Right(value)         => value.some.asRight
            case Left(e)              => e.asLeft
          }

        def extractFPU(
          config: CleanConfig
        ): Either[ConfigUtilOps.ExtractFailure, Option[NorthTypes#FPU]] =
          config.extractInstAs[NorthTypes#FPU](FPU_PROP_NAME) match {
            case Left(KeyNotFound(_)) => none.asRight
            case Right(value)         => value.some.asRight
            case Left(e)              => e.asLeft
          }

        def extractStageMode(
          config: CleanConfig
        ): Either[ConfigUtilOps.ExtractFailure, NorthTypes#GmosStageMode] =
          config.extractInstAs[NorthTypes#GmosStageMode](STAGE_MODE_PROP)
      },
      nsCmdR
    )(
      northConfigTypes
    ) {
  override val resource: Instrument      = Instrument.GmosN
  override val dhsInstrumentName: String = "GMOS-N"

}

object GmosNorth {
  val name: String = INSTRUMENT_NAME_PROP

  def apply[F[_]: Temporal: Logger](
    c:         GmosController[F, NorthTypes],
    dhsClient: DhsClient[F],
    nsCmdR:    Ref[F, Option[NSObserveCommand]]
  ): GmosNorth[F] = new GmosNorth[F](c, dhsClient, nsCmdR)

  object specifics extends InstrumentSpecifics {
    override val instrument: Instrument = Instrument.GmosN

    override def calcStepType(
      config:     CleanConfig,
      isNightSeq: Boolean
    ): Either[ObserveFailure, StepType] =
      Gmos.calcStepType(instrument, config, isNightSeq)

    override def sfName(config: CleanConfig): LightSinkName = LightSinkName.Gmos

    // TODO Use different value if using electronic offsets
    override val oiOffsetGuideThreshold: Option[Length] =
      (Arcseconds(0.01) / FOCAL_PLANE_SCALE).some

  }

}
