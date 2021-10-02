// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.niri

import java.lang.{ Double => JDouble }
import java.lang.{ Integer => JInt }
import cats.data.EitherT
import cats.data.Kleisli
import cats.effect.Sync
import cats.syntax.all._
import edu.gemini.observe.server.niri.ReadMode
import edu.gemini.spModel.gemini.niri.InstNIRI._
import edu.gemini.spModel.gemini.niri.Niri.Camera
import edu.gemini.spModel.gemini.niri.Niri.WellDepth
import edu.gemini.spModel.gemini.niri.Niri.{ ReadMode => OCSReadMode }
import edu.gemini.spModel.obscomp.InstConstants.BIAS_OBSERVE_TYPE
import edu.gemini.spModel.obscomp.InstConstants.DARK_OBSERVE_TYPE
import edu.gemini.spModel.obscomp.InstConstants.OBSERVE_TYPE_PROP
import org.typelevel.log4cats.Logger
import lucuma.core.enum.LightSinkName
import observe.model.dhs.ImageFileId
import observe.model.enum.Instrument
import observe.model.enum.ObserveCommandResult
import observe.server.CleanConfig
import observe.server.CleanConfig.extractItem
import observe.server.ConfigResult
import observe.server.ConfigUtilOps
import observe.server.ConfigUtilOps.ExtractFailure
import observe.server.ConfigUtilOps._
import observe.server.InstrumentActions
import observe.server.InstrumentSpecifics
import observe.server.InstrumentSystem
import observe.server.InstrumentSystem.AbortObserveCmd
import observe.server.InstrumentSystem.StopObserveCmd
import observe.server.InstrumentSystem.UnpausableControl
import observe.server.Progress
import observe.server.ObserveFailure
import observe.server.keywords.DhsClient
import observe.server.keywords.DhsInstrument
import observe.server.keywords.KeywordsClient
import observe.server.niri.NiriController._
import observe.server.tcs.FOCAL_PLANE_SCALE
import squants.Length
import squants.Time
import squants.space.Arcseconds
import squants.time.TimeConversions._
import cats.effect.Async

final case class Niri[F[_]: Async: Logger](
  controller: NiriController[F],
  dhsClient:  DhsClient[F]
) extends DhsInstrument[F]
    with InstrumentSystem[F] {

  import Niri._

  override val contributorName: String                                                 = "mko-dc-data-niri"
  override def observeControl(config: CleanConfig): InstrumentSystem.ObserveControl[F] =
    UnpausableControl(
      StopObserveCmd(_ => controller.stopObserve),
      AbortObserveCmd(controller.abortObserve)
    )

  override def observe(config: CleanConfig): Kleisli[F, ImageFileId, ObserveCommandResult] =
    Kleisli { fileId =>
      EitherT
        .fromEither[F](getDCConfig(config))
        .widenRethrowT
        .flatMap(controller.observe(fileId, _))
    }

  override def calcObserveTime(config: CleanConfig): F[Time] =
    getDCConfig(config)
      .map(controller.calcTotalExposureTime)
      .getOrElse(60.seconds.pure[F])

  override def observeProgress(
    total:   Time,
    elapsed: InstrumentSystem.ElapsedTime
  ): fs2.Stream[F, Progress] = controller.observeProgress(total)

  override val dhsInstrumentName: String = "NIRI"

  override val keywordsClient: KeywordsClient[F] = this

  override val resource: Instrument = Instrument.Niri

  /**
   * Called to configure a system
   */
  override def configure(config: CleanConfig): F[ConfigResult[F]] =
    EitherT
      .fromEither[F](fromSequenceConfig(config))
      .widenRethrowT
      .flatMap(controller.applyConfig)
      .as(ConfigResult(this))

  override def notifyObserveStart: F[Unit] = Sync[F].unit

  override def notifyObserveEnd: F[Unit] =
    controller.endObserve

  override def instrumentActions(config: CleanConfig): InstrumentActions[F] =
    InstrumentActions.defaultInstrumentActions[F]
}

object Niri {
  val name: String = INSTRUMENT_NAME_PROP

  def extractExposureTime(config: CleanConfig): Either[ExtractFailure, Time] =
    config.extractObsAs[JDouble](EXPOSURE_TIME_PROP).map(_.toDouble.seconds)

  def extractCoadds(config: CleanConfig): Either[ExtractFailure, Int] =
    config.extractObsAs[JInt](COADDS_PROP).map(_.toInt)

  def calcReadMode(
    readMode:  OCSReadMode,
    wellDepth: WellDepth
  ): Either[ConfigUtilOps.ExtractFailure, ReadMode] = {
    import OCSReadMode._
    import WellDepth._
    (readMode, wellDepth) match {
      case (IMAG_SPEC_NB, SHALLOW)   => ReadMode.LowRN.asRight
      case (IMAG_1TO25, SHALLOW)     => ReadMode.MedRN.asRight
      case (IMAG_SPEC_3TO5, SHALLOW) => ReadMode.HighRN.asRight
      case (IMAG_1TO25, DEEP)        => ReadMode.MedRNDeep.asRight
      case (IMAG_SPEC_3TO5, DEEP)    => ReadMode.ThermalIR.asRight
      case _                         =>
        ContentError(
          s"Combination not supported: readMode = " +
            s"${readMode.displayValue}, wellDepth = ${wellDepth.displayValue}"
        ).asLeft
    }
  }

  def getCameraConfig(config: CleanConfig): Either[ExtractFailure, Camera] =
    config.extractInstAs[Camera](CAMERA_PROP)

  def getCCCommonConfig(config: CleanConfig): Either[ObserveFailure, Common] = (for {
    cam <- getCameraConfig(config)
    bms <- config.extractInstAs[BeamSplitter](BEAM_SPLITTER_PROP)
    foc <- config.extractInstAs[Focus](FOCUS_PROP)
    dsp <- config.extractInstAs[Disperser](DISPERSER_PROP)
    msk <- config.extractInstAs[Mask](MASK_PROP)
  } yield Common(cam, bms, foc, dsp, msk))
    .leftMap(e => ObserveFailure.Unexpected(ConfigUtilOps.explain(e)))

  def getCCIlluminatedConfig(config: CleanConfig): Either[ObserveFailure, Illuminated] = {
    val filter = (for {
      f  <- config.extractInstAs[Filter](FILTER_PROP)
      fl <- if (f.isObsolete) ContentError(s"Obsolete filter ${f.displayValue}").asLeft
            else f.asRight
    } yield fl)
      .leftMap(e => ObserveFailure.Unexpected(ConfigUtilOps.explain(e)))

    (filter, getCCCommonConfig(config)).mapN(Illuminated)
  }

  def getCCDarkConfig(config: CleanConfig): Either[ObserveFailure, Dark] =
    getCCCommonConfig(config).map(Dark)

  def getCCConfig(config: CleanConfig): Either[ObserveFailure, CCConfig] =
    config
      .extractObsAs[String](OBSERVE_TYPE_PROP)
      .leftMap(e => ObserveFailure.Unexpected(ConfigUtilOps.explain(e)))
      .flatMap {
        case DARK_OBSERVE_TYPE => getCCDarkConfig(config)
        case BIAS_OBSERVE_TYPE => ObserveFailure.Unexpected("Bias not supported for NIRI").asLeft
        case _                 => getCCIlluminatedConfig(config)
      }

  def getDCConfig(config: CleanConfig): Either[ObserveFailure, DCConfig] = (for {
    expTime    <- extractExposureTime(config)
    coadds     <- extractCoadds(config)
    rm         <- config.extractInstAs[OCSReadMode](READ_MODE_PROP)
    wellDepth  <- config.extractInstAs[WellDepth](WELL_DEPTH_PROP)
    readMode   <- calcReadMode(rm, wellDepth)
    builtInROI <- config.extractInstAs[BuiltInROI](BUILTIN_ROI_PROP)
  } yield DCConfig(expTime, coadds, readMode, builtInROI))
    .leftMap(e => ObserveFailure.Unexpected(ConfigUtilOps.explain(e)))

  def fromSequenceConfig(config: CleanConfig): Either[ObserveFailure, NiriConfig] = for {
    cc <- getCCConfig(config)
    dc <- getDCConfig(config)
  } yield NiriConfig(cc, dc)

  object specifics extends InstrumentSpecifics {
    override val instrument: Instrument = Instrument.Niri

    override def sfName(config: CleanConfig): LightSinkName = getCameraConfig(config)
      .map {
        case Camera.F6                  => LightSinkName.Niri_f6
        case Camera.F14                 => LightSinkName.Niri_f14
        case Camera.F32 | Camera.F32_PV => LightSinkName.Niri_f32
      }
      .getOrElse(LightSinkName.Niri_f6)

    override val oiOffsetGuideThreshold: Option[Length] =
      (Arcseconds(0.01) / FOCAL_PLANE_SCALE).some

  }

}
