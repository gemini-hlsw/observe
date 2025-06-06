// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.flamingos2

import cats.data.Kleisli
import cats.effect.Async
import cats.effect.Sync
import cats.syntax.all.*
import coulomb.Quantity
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import coulomb.units.accepted.Millimeter
import fs2.Stream
import lucuma.core.enums.Flamingos2Disperser
import lucuma.core.enums.Flamingos2Fpu
import lucuma.core.enums.Flamingos2WindowCover
import lucuma.core.enums.Instrument
import lucuma.core.enums.LightSinkName
import lucuma.core.enums.StepType as CoreStepType
import lucuma.core.model.sequence.flamingos2.Flamingos2DynamicConfig
import lucuma.core.model.sequence.flamingos2.Flamingos2FpuMask
import lucuma.core.model.sequence.flamingos2.Flamingos2StaticConfig
import lucuma.core.util.TimeSpan
import observe.model.dhs.ImageFileId
import observe.model.enums.ObserveCommandResult
import observe.server.*
import observe.server.flamingos2.Flamingos2Controller.*
import observe.server.keywords.DhsClient
import observe.server.keywords.DhsClientProvider
import observe.server.keywords.DhsInstrument
import observe.server.keywords.KeywordsClient
import observe.server.tcs.FOCAL_PLANE_SCALE
import observe.server.tcs.FocalPlaneScale.*
import org.typelevel.log4cats.Logger

final case class Flamingos2[F[_]: Async: Logger](
  controller:        Flamingos2Controller[F],
  dhsClientProvider: DhsClientProvider[F],
  config:            Flamingos2Controller.Flamingos2Config
) extends DhsInstrument[F]
    with InstrumentSystem[F] {
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

  private def windowCoverFromStepType(stepType: CoreStepType): Flamingos2WindowCover =
    stepType match
      case CoreStepType.Dark => Flamingos2WindowCover.Close
      case _                 => Flamingos2WindowCover.Open

  private def grismFromSPDisperser(disperser: Option[Flamingos2Disperser]): Grism =
    disperser match
      case None                              => Grism.Open
      case Some(Flamingos2Disperser.R1200HK) => Grism.R1200HK
      case Some(Flamingos2Disperser.R1200JH) => Grism.R1200JH
      case Some(Flamingos2Disperser.R3000)   => Grism.R3000

  private def grismFromDisperserAndStepType(
    disperser: Option[Flamingos2Disperser],
    stepType:  CoreStepType
  ): Grism =
    stepType match
      case CoreStepType.Dark => Grism.Dark
      case _                 => grismFromSPDisperser(disperser)

  private def ccConfigFromSequenceConfig(
    dynamicConfig: Flamingos2DynamicConfig,
    stepType:      CoreStepType
  ): CCConfig =
    CCConfig(
      windowCoverFromStepType(stepType),
      dynamicConfig.decker,
      fpuFromFpuMask(dynamicConfig.fpu),
      dynamicConfig.filter,
      dynamicConfig.lyotWheel,
      grismFromDisperserAndStepType(dynamicConfig.disperser, stepType)
    )

  private def dcConfigFromSequenceConfig(dynamicConfig: Flamingos2DynamicConfig): DCConfig =
    DCConfig(
      dynamicConfig.exposure,
      dynamicConfig.reads,
      dynamicConfig.readoutMode,
      dynamicConfig.decker
    )

  private def fromSequenceConfig[F[_]](
    dynamicConfig: Flamingos2DynamicConfig,
    stepType:      CoreStepType
  ): Flamingos2Config =
    Flamingos2Config(
      ccConfigFromSequenceConfig(dynamicConfig, stepType),
      dcConfigFromSequenceConfig(dynamicConfig)
    )

  def build[F[_]: Async: Logger](
    controller:        Flamingos2Controller[F],
    dhsClientProvider: DhsClientProvider[F],
    stepType:          CoreStepType,
    dynamicConfig:     Flamingos2DynamicConfig
  ): Flamingos2[F] = Flamingos2(
    controller,
    dhsClientProvider,
    fromSequenceConfig(dynamicConfig, stepType)
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
