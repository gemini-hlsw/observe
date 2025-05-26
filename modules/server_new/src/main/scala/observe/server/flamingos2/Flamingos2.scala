// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.flamingos2

import cats.data.Kleisli
import cats.effect.Sync
import cats.syntax.all._
import cats.effect.Async
import fs2.Stream
import org.typelevel.log4cats.Logger
import lucuma.core.enums.LightSinkName
import observe.model.dhs.ImageFileId
import lucuma.core.enums.Instrument
import observe.model.enums.ObserveCommandResult
import observe.server._
import observe.server.flamingos2.Flamingos2Controller._
import observe.server.keywords.{DhsClient, DhsClientProvider, DhsInstrument, KeywordsClient}
import observe.server.tcs.FOCAL_PLANE_SCALE
import lucuma.core.util.TimeSpan
import lucuma.core.enums.Flamingos2WindowCover
import lucuma.core.model.sequence.flamingos2.Flamingos2StaticConfig
import lucuma.core.model.sequence.flamingos2.Flamingos2DynamicConfig
import lucuma.core.enums.Flamingos2Disperser
import observe.model.enums.ExecutionStepType
import lucuma.core.enums.Flamingos2Fpu
import lucuma.core.model.sequence.flamingos2.Flamingos2FpuMask
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import coulomb.units.accepted.Millimeter
import coulomb.Quantity
import observe.server.tcs.FocalPlaneScale.*
import observe.server.gsaoi.WindowCover

final case class Flamingos2[F[_]: Async: Logger](
  controller:        Flamingos2Controller[F],
  dhsClientProvider: DhsClientProvider[F],
  config:            Flamingos2Controller.Flamingos2Config
) extends DhsInstrument[F]
    with InstrumentSystem[F] {

  import Flamingos2._

  override val resource: Instrument = Instrument.Flamingos2

  override val contributorName: String = "flamingos2"

  override val dhsInstrumentName: String = "F2"

  override val dhsClient: DhsClient[F] = dhsClientProvider.dhsClient(dhsInstrumentName)

  override val keywordsClient: KeywordsClient[F] = this

  override def observeControl: InstrumentSystem.ObserveControl[F] =
    InstrumentSystem.Uncontrollable

  // FLAMINGOS-2 does not support abort or stop.
  override def observe: Kleisli[F, ImageFileId, ObserveCommandResult] =
    Kleisli { fileId =>
      controller.observe(fileId, config.dc.exposureTime)
    }

  override def configure: F[ConfigResult[F]] =
    controller
      .applyConfig(config)
      .as(ConfigResult(this))

  override def notifyObserveEnd: F[Unit] =
    controller.endObserve

  override def notifyObserveStart: F[Unit] = Sync[F].unit

  override def calcObserveTime: TimeSpan =
    config.dc.exposureTime

  override def observeProgress(
    total:   TimeSpan,
    elapsed: InstrumentSystem.ElapsedTime
  ): Stream[F, Progress] =
    controller.observeProgress(total)

  override def instrumentActions: InstrumentActions[F] =
    InstrumentActions.defaultInstrumentActions[F]

}

object Flamingos2 {

  private def fpuFromFpuMask(fpu: Flamingos2FpuMask): FocalPlaneUnit = fpu match {
    case Flamingos2FpuMask.Imaging        => FocalPlaneUnit.Open
    case Flamingos2FpuMask.Builtin(value) =>
      value match
        case Flamingos2Fpu.SubPixPinhole => FocalPlaneUnit.GridSub1Pix
        case Flamingos2Fpu.Pinhole       => FocalPlaneUnit.Grid2Pix
        case Flamingos2Fpu.LongSlit1     => FocalPlaneUnit.Slit1Pix
        case Flamingos2Fpu.LongSlit2     => FocalPlaneUnit.Slit2Pix
        case Flamingos2Fpu.LongSlit3     => FocalPlaneUnit.Slit3Pix
        case Flamingos2Fpu.LongSlit4     => FocalPlaneUnit.Slit4Pix
        case Flamingos2Fpu.LongSlit6     => FocalPlaneUnit.Slit6Pix
        case Flamingos2Fpu.LongSlit8     => FocalPlaneUnit.Slit8Pix
    case Flamingos2FpuMask.Custom(_, _)   => FocalPlaneUnit.Custom("")
  }

  // TODO Revisit this once we have Dark Observations
  // private def windowCoverFromObsType(observationType: ObservationType): Flamingos2WindowCover =
  //   observationType match
  //     case ObservationType.Dark => Flamingos2WindowCover.Close
  //     case _                      => Flamingos2WindowCover.Open

  private def grismFromSPDisperser(disperser: Option[Flamingos2Disperser]): Grism =
    disperser match
      case None                              => Grism.Open
      case Some(Flamingos2Disperser.R1200HK) => Grism.R1200HK
      case Some(Flamingos2Disperser.R1200JH) => Grism.R1200JH
      case Some(Flamingos2Disperser.R3000)   => Grism.R3000

  // TODO Revisit this once we have Dark Observations
  // private def grismFromDisperserAndObserveType(
  //   disperser: Option[Flamingos2Disperser],
  //   observationType: ObservationType
  // ): Grism =
  //   observationType match
  //     case ObservationType.Dark => Grism.Dark
  //     case _                      => grismFromSPDisperser(disperser)

  private def ccConfigFromSequenceConfig(dynamicConfig: Flamingos2DynamicConfig): CCConfig =
    CCConfig(
      // windowCoverFromObsType(observationType), // TODO: Revisit this once we have Dark Observations
      Flamingos2WindowCover.Open, // For now we always use Open
      dynamicConfig.decker,
      fpuFromFpuMask(dynamicConfig.fpu),
      dynamicConfig.filter,
      dynamicConfig.lyotWheel,
      // grismFromDisperserAndObserveType(dynamicConfig.disperser, observationType) // TODO: Revisit this once we have Dark Observations
      grismFromSPDisperser(dynamicConfig.disperser)
    )

  private def dcConfigFromSequenceConfig(dynamicConfig: Flamingos2DynamicConfig): DCConfig =
    DCConfig(
      dynamicConfig.exposure,
      dynamicConfig.reads,
      dynamicConfig.readoutMode,
      dynamicConfig.decker
    )

  private def fromSequenceConfig[F[_]](dynamicConfig: Flamingos2DynamicConfig): Flamingos2Config =
    Flamingos2Config(
      ccConfigFromSequenceConfig(dynamicConfig),
      dcConfigFromSequenceConfig(dynamicConfig)
    )

  def build[F[_]: Async: Logger](
    controller:        Flamingos2Controller[F],
    dhsClientProvider: DhsClientProvider[F],
    dynamicConfig:     Flamingos2DynamicConfig
  ): Flamingos2[F] = Flamingos2(
    controller,
    dhsClientProvider,
    fromSequenceConfig(dynamicConfig)
  )

  object specifics extends InstrumentSpecifics[Flamingos2StaticConfig, Flamingos2DynamicConfig] {
    override val instrument: Instrument = Instrument.Flamingos2

    // TODO Use different value if using electronic offsets
    override val oiOffsetGuideThreshold: Option[Quantity[Double, Millimeter]] =
      (0.01.withUnit[ArcSecond] :\ FOCAL_PLANE_SCALE).some

    // The name used for this instrument in the science fold configuration
    override def sfName(config: Flamingos2DynamicConfig): LightSinkName = LightSinkName.Flamingos2
  }

}
