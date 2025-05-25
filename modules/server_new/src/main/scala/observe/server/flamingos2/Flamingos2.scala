// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.flamingos2

import java.lang.{Double => JDouble}
import scala.concurrent.duration.Duration
import scala.concurrent.duration.SECONDS
import scala.reflect.ClassTag
import cats.Applicative
import cats.data.EitherT
import cats.data.Kleisli
import cats.effect.Sync
import cats.syntax.all._
import cats.effect.Async
import lucuma.core.enums.Flamingos2Reads
// import edu.gemini.spModel.obscomp.InstConstants.DARK_OBSERVE_TYPE
// import edu.gemini.spModel.obscomp.InstConstants.OBSERVE_TYPE_PROP
// import edu.gemini.spModel.seqcomp.SeqConfigNames._
import fs2.Stream
import org.typelevel.log4cats.Logger
import lucuma.core.enums.LightSinkName
import observe.model.dhs.ImageFileId
import lucuma.core.enums.Instrument
import observe.model.enums.ObserveCommandResult
// import observe.server.ConfigUtilOps._
import observe.server._
import observe.server.flamingos2.Flamingos2Controller._
import observe.server.keywords.{DhsClient, DhsClientProvider, DhsInstrument, KeywordsClient}
import observe.server.tcs.FOCAL_PLANE_SCALE
// import squants.Length
// import squants.space.Arcseconds
// import squants.time.Seconds
// import squants.time.Time
import lucuma.core.util.TimeSpan
import lucuma.core.enums.Flamingos2ReadMode
import lucuma.core.enums.Flamingos2Decker
import lucuma.core.enums.Flamingos2WindowCover
import lucuma.core.model.sequence.flamingos2.Flamingos2StaticConfig
import lucuma.core.model.sequence.flamingos2.Flamingos2DynamicConfig
import lucuma.core.enums.Flamingos2Disperser

final case class Flamingos2[F[_]: Async: Logger](
  f2Controller:      Flamingos2Controller[F],
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

  override val sequenceComplete: F[Unit] = Applicative[F].unit

  override def observeControl: InstrumentSystem.ObserveControl[F] =
    InstrumentSystem.Uncontrollable

  // FLAMINGOS-2 does not support abort or stop.
  override def observe: Kleisli[F, ImageFileId, ObserveCommandResult] =
    Kleisli { fileId =>
      f2Controller.observe(fileId, config.dc.t)
    }

  override def configure: F[ConfigResult[F]] =
    EitherT
      .fromEither[F](fromSequenceConfig)
      .widenRethrowT
      .flatMap(f2Controller.applyConfig)
      .as(ConfigResult(this))

  override def notifyObserveEnd: F[Unit] =
    f2Controller.endObserve

  override def notifyObserveStart: F[Unit] = Sync[F].unit

  override def calcObserveTime: F[TimeSpan] =
    Sync[F].delay(
      config.dc.t
        //   config
        //     .extractObsAs[JDouble](EXPOSURE_TIME_PROP)
        //     .map(x => Seconds(x.toDouble))
        //     .getOrElse(Seconds(360))
    )

  override def observeProgress(
    total:   TimeSpan,
    elapsed: InstrumentSystem.ElapsedTime
  ): Stream[F, Progress] =
    f2Controller.observeProgress(total)

  override def instrumentActions: InstrumentActions[F] =
    InstrumentActions.defaultInstrumentActions[F]

}

object Flamingos2 {
  val name: String = INSTRUMENT_NAME_PROP

  def fpuFromFPUnit(fpu: FPUnit): FocalPlaneUnit = fpu match {
    case FPUnit.FPU_NONE       => FocalPlaneUnit.Open
    case FPUnit.SUBPIX_PINHOLE => FocalPlaneUnit.GridSub1Pix
    case FPUnit.PINHOLE        => FocalPlaneUnit.Grid2Pix
    case FPUnit.LONGSLIT_1     => FocalPlaneUnit.Slit1Pix
    case FPUnit.LONGSLIT_2     => FocalPlaneUnit.Slit2Pix
    case FPUnit.LONGSLIT_3     => FocalPlaneUnit.Slit3Pix
    case FPUnit.LONGSLIT_4     => FocalPlaneUnit.Slit4Pix
    case FPUnit.LONGSLIT_6     => FocalPlaneUnit.Slit6Pix
    case FPUnit.LONGSLIT_8     => FocalPlaneUnit.Slit8Pix
    case FPUnit.CUSTOM_MASK    => FocalPlaneUnit.Custom("")
  }

  def readsFromReadMode(readMode: Flamingos2ReadMode): Flamingos2Reads = readMode match {
    case Flamingos2ReadMode.Bright => Flamingos2Reads.Reads_1
    case Flamingos2ReadMode.Medium => Flamingos2Reads.Reads_4
    case Flamingos2ReadMode.Faint  => Flamingos2Reads.Reads_8
  }

  implicit def biasFromDecker(dk: Flamingos2Decker): BiasMode = dk match {
    case Flamingos2Decker.Imaging  => BiasMode.Imaging
    case Flamingos2Decker.LongSlit => BiasMode.LongSlit
    case Flamingos2Decker.MOS      => BiasMode.MOS
  }

  def fpuConfig: Either[ConfigUtilOps.ExtractFailure, FocalPlaneUnit] = {
    val a = FPU_PROP
    val b = FPU_MASK_PROP

    config
      .extractInstAs[FPUnit](a)
      .flatMap(x =>
        if (x =!= FPUnit.CUSTOM_MASK) fpuFromFPUnit(x).asRight
        else config.extractInstAs[String](b).map(FocalPlaneUnit.Custom)
      )
  }

  def windowCoverFromObserveType(observeType: String): Flamingos2WindowCover = observeType match {
    case DARK_OBSERVE_TYPE => Flamingos2WindowCover.Close
    case _                 => Flamingos2WindowCover.Open
  }

  implicit def grismFromSPDisperser(d: Option[Flamingos2Disperser]): Grism = d match {
    case None                              => Grism.Open
    case Some(Flamingos2Disperser.R1200HK) => Grism.R1200HK
    case Some(Flamingos2Disperser.R1200JH) => Grism.R1200JH
    case Some(Flamingos2Disperser.R3000)   => Grism.R3000
  }

  private def disperserFromObserveType(observeType: String, d: Option[Flamingos2Disperser]): Grism =
    observeType match {
      case DARK_OBSERVE_TYPE => Grism.Dark
      case _                 => grismFromSPDisperser(d)
    }

  // This method deals with engineering parameters that can come as a T or an Option[T]
  // private def extractEngineeringParam[T](item: Extracted[CleanConfig], default: T)(implicit
  //   clazz: ClassTag[T]
  // ): Either[ExtractFailure, T] = item.as[T].recoverWith {
  //   case _: ConfigUtilOps.KeyNotFound     => Right(default)
  //   case _: ConfigUtilOps.ConversionError =>
  //     item
  //       .as[edu.gemini.shared.util.immutable.Option[T]]
  //       .map(_.getOrElse(default))
  // }

  // def ccConfigFromSequenceConfig: Either[ObserveFailure, CCConfig] =
  //   (for {
  //     obsType <- config.extractObsAs[String](OBSERVE_TYPE_PROP)
  //     // WINDOW_COVER_PROP is optional. It can be a WindowCover, an Option[WindowCover], or not be present. If no
  //     // value is given, then window cover position is inferred from observe type.
  //     p       <- extractEngineeringParam(config.extract(INSTRUMENT_KEY / WINDOW_COVER_PROP),
  //                                        windowCoverFromObserveType(obsType)
  //                )
  //     q       <- config.extractInstAs[Decker](DECKER_PROP)
  //     r       <- fpuConfig(config)
  //     f       <- config.extractInstAs[Filter](FILTER_PROP)
  //     s       <- if (f.isObsolete) ContentError(s"Obsolete filter ${f.displayValue}").asLeft
  //                else f.asRight
  //     t       <- config.extractInstAs[LyotWheel](LYOT_WHEEL_PROP)
  //     u       <- config.extractInstAs[Disperser](DISPERSER_PROP).map(disperserFromObserveType(obsType, _))
  //   } yield CCConfig(p, q, r, s, t, u)).leftMap(e =>
  //     SeqexecFailure.Unexpected(ConfigUtilOps.explain(e))
  // )

  def ccConfigFromSequenceConfig(staticConfig: Flamingos2StaticConfig): CCConfig =
    CCConfig(
      config.windowCover,
      config.decker,
      fpuFromFPUnit(config.fpu),
      config.filter,
      config.lyotWheel,
      config.grism
    )

  def dcConfigFromSequenceConfig(dynamicConfig: Flamingos2DynamicConfig): DCConfig =
    DCConfig(
      dynamicConfig.exposure,
      dynamicConfig.reads,
      dynamicConfig.readoutMode,
      dynamicConfig.decker
    )

  def fromSequenceConfig[F[_]]: Either[ObserveFailure, Flamingos2Config] = for {
    p <- ccConfigFromSequenceConfig(config)
    q <- dcConfigFromSequenceConfig(config)
  } yield Flamingos2Config(p, q)

  object specifics extends InstrumentSpecifics {
    override val instrument: Instrument = Instrument.Flamingos2

    // TODO Use different value if using electronic offsets
    override val oiOffsetGuideThreshold: Option[Length] =
      (Arcseconds(0.01) / FOCAL_PLANE_SCALE).some

    override def sfName: LightSinkName = LightSinkName.F2

  }

}
