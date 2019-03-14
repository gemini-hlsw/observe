// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.gnirs

import cats.data.Reader
import cats.implicits._
import cats.effect.IO
import edu.gemini.spModel.config2.Config
import edu.gemini.spModel.gemini.gnirs.GNIRSConstants.{INSTRUMENT_NAME_PROP, WOLLASTON_PRISM_PROP}
import edu.gemini.spModel.gemini.gnirs.GNIRSParams._
import edu.gemini.spModel.gemini.gnirs.InstGNIRS._
import edu.gemini.spModel.obscomp.InstConstants.{BIAS_OBSERVE_TYPE, DARK_OBSERVE_TYPE, OBSERVE_TYPE_PROP}
import edu.gemini.spModel.seqcomp.SeqConfigNames.{INSTRUMENT_KEY, OBSERVE_KEY}
import java.lang.{Double => JDouble, Integer => JInt}

import gem.enum.LightSinkName
import seqexec.model.enum.Instrument
import seqexec.model.dhs.ImageFileId
import seqexec.server.ConfigUtilOps._
import seqexec.server.gnirs.GnirsController.{CCConfig, DCConfig, Other, ReadMode}
import seqexec.server._
import seqexec.server.keywords.{DhsClient, DhsInstrument, KeywordsClient}
import squants.Time
import squants.space.LengthConversions._
import squants.time.TimeConversions._

final case class Gnirs(controller: GnirsController, dhsClient: DhsClient[IO]) extends DhsInstrument[IO] with InstrumentSystem[IO] {
  override def sfName(config: Config): LightSinkName = LightSinkName.Gnirs
  override val contributorName: String = "ngnirsdc1"
  override val dhsInstrumentName: String = "GNIRS"

  override val keywordsClient: KeywordsClient[IO] = this

  import Gnirs._
  import InstrumentSystem._
  override val observeControl: ObserveControl = InfraredControl(StopObserveCmd(controller.stopObserve),
                                                                AbortObserveCmd(controller.abortObserve))

  override def observe(config: Config): SeqObserve[ImageFileId, ObserveCommand.Result] =
    Reader { fileId =>
      SeqActionF.liftF(calcObserveTime(config)).flatMap {
        controller.observe(fileId, _)
      }
    }

  override def calcObserveTime(config: Config): IO[Time] =
    getDCConfig(config)
      .map(controller.calcTotalExposureTime[IO])
      .getOrElse(IO.pure(60.seconds))

  override val resource: Instrument = Instrument.Gnirs

  override def configure(config: Config): SeqAction[ConfigResult[IO]] =
    SeqAction.either(fromSequenceConfig(config)).flatMap(controller.applyConfig).as(ConfigResult(this))

  override def notifyObserveEnd: SeqAction[Unit] = controller.endObserve

  override def notifyObserveStart: SeqAction[Unit] = SeqAction.void

  override def observeProgress(total: Time, elapsed: ElapsedTime): fs2.Stream[IO, Progress] =
    controller.observeProgress(total)
}

object Gnirs {

  val name: String = INSTRUMENT_NAME_PROP

  def extractExposureTime(config: Config): Either[ExtractFailure, Time] =
    config.extractAs[JDouble](OBSERVE_KEY / EXPOSURE_TIME_PROP).map(_.toDouble.seconds)

  def extractCoadds(config: Config): Either[ExtractFailure, Int] =
    config.extractAs[JInt](OBSERVE_KEY / COADDS_PROP).map(_.toInt)

  def fromSequenceConfig(config: Config): TrySeq[GnirsController.GnirsConfig] =
    (getCCConfig(config), getDCConfig(config)).mapN(GnirsController.GnirsConfig)

  private def getDCConfig(config: Config): TrySeq[DCConfig] = (for {
    expTime <- extractExposureTime(config)
    coadds  <- extractCoadds(config)
    readMode <- config.extractAs[ReadMode](INSTRUMENT_KEY / READ_MODE_PROP)
    wellDepth <- config.extractAs[WellDepth](INSTRUMENT_KEY / WELL_DEPTH_PROP)
  } yield DCConfig(expTime, coadds, readMode, wellDepth))
    .leftMap(e => SeqexecFailure.Unexpected(ConfigUtilOps.explain(e)))

  private def getCCConfig(config: Config): TrySeq[CCConfig] = config.extractAs[String](OBSERVE_KEY / OBSERVE_TYPE_PROP)
    .leftMap(e => SeqexecFailure.Unexpected(ConfigUtilOps.explain(e))).flatMap{
    case DARK_OBSERVE_TYPE => GnirsController.Dark.asRight
    case BIAS_OBSERVE_TYPE => SeqexecFailure.Unexpected("Bias not supported for GNIRS").asLeft
    case _                 => getCCOtherConfig(config)
  }

  private def getCCOtherConfig(config: Config): TrySeq[CCConfig] = (for {
    xdisp  <- config.extractAs[CrossDispersed](INSTRUMENT_KEY / CROSS_DISPERSED_PROP)
    woll   <- config.extractAs[WollastonPrism](INSTRUMENT_KEY / WOLLASTON_PRISM_PROP)
    mode   <- getCCMode(config, xdisp, woll)
    slit   <- config.extractAs[SlitWidth](INSTRUMENT_KEY / SLIT_WIDTH_PROP)
    slitOp = getSlit(slit)
    camera <- config.extractAs[Camera](INSTRUMENT_KEY / CAMERA_PROP)
    decker <- getDecker(config, slit, woll, xdisp)
    wavel  <- config.extractAs[Wavelength](INSTRUMENT_KEY / CENTRAL_WAVELENGTH_PROP).map(_.doubleValue().nanometers)
    filter <- config.extractAs[Filter](INSTRUMENT_KEY / FILTER_PROP).toOption.asRight
    filter1 = getFilter1(filter, slit, decker)
    filter2 = getFilter2(filter, xdisp)
  } yield Other(mode, camera, decker, filter1, filter2, wavel, slitOp) )
    .leftMap(e => SeqexecFailure.Unexpected(ConfigUtilOps.explain(e)))

  private def getCCMode(config: Config, xdispersed: CrossDispersed, woll: WollastonPrism): Either[ConfigUtilOps.ExtractFailure, GnirsController.Mode] = for {
    acq        <- config.extractAs[AcquisitionMirror](INSTRUMENT_KEY / ACQUISITION_MIRROR_PROP)
    disperser  <- config.extractAs[Disperser](INSTRUMENT_KEY / DISPERSER_PROP)
  } yield {
    if(acq === AcquisitionMirror.IN) GnirsController.Acquisition
    else xdispersed match {
      case CrossDispersed.SXD => GnirsController.CrossDisperserS(disperser)
      case CrossDispersed.LXD => GnirsController.CrossDisperserL(disperser)
      case _                  => if(woll === WollastonPrism.YES) GnirsController.Wollaston(disperser)
                                 else GnirsController.Mirror(disperser)
    }
  }

  private def getDecker(config: Config, slit: SlitWidth, woll: WollastonPrism, xdisp: CrossDispersed): Either[ConfigUtilOps.ExtractFailure, GnirsController.Decker] =
    config.extractAs[Decker](INSTRUMENT_KEY / DECKER_PROP).orElse {
      for {
        pixScale <- config.extractAs[PixelScale](INSTRUMENT_KEY / PIXEL_SCALE_PROP)
      } yield xdisp match {
        case CrossDispersed.LXD => Decker.LONG_CAM_X_DISP
        case CrossDispersed.SXD => Decker.SHORT_CAM_X_DISP
        case _                  =>
          if (woll === WollastonPrism.YES) Decker.WOLLASTON
          else pixScale match {
            case PixelScale.PS_005 => Decker.LONG_CAM_LONG_SLIT
            case PixelScale.PS_015 =>
              if (slit === SlitWidth.IFU) Decker.IFU
              else Decker.SHORT_CAM_LONG_SLIT
          }
      }
    }

  private def getFilter1(filter: Option[Filter], slit: SlitWidth, decker: Decker): GnirsController.Filter1 =
    if(slit === SlitWidth.PUPIL_VIEWER || decker === Decker.PUPIL_VIEWER) GnirsController.Filter1.PupilViewer
    else filter.map{
      case Filter.H2_plus_ND100X | Filter.H_plus_ND100X => GnirsController.Filter1.ND100X
      case Filter.Y                                     => GnirsController.Filter1.Y_MK
      case Filter.J                                     => GnirsController.Filter1.J_MK
      case Filter.K                                     => GnirsController.Filter1.K_MK
      case _                                            => GnirsController.Filter1.Open
    }.getOrElse(GnirsController.Filter1.Open)

  private def getFilter2(filter: Option[Filter], xdisp: CrossDispersed): GnirsController.Filter2 =
    filter.map{
      case Filter.ORDER_1        => GnirsController.Filter2Pos.M
      case Filter.ORDER_2        => GnirsController.Filter2Pos.L
      case Filter.ORDER_3        => GnirsController.Filter2Pos.K
      case Filter.ORDER_4        => GnirsController.Filter2Pos.H
      case Filter.ORDER_5        => GnirsController.Filter2Pos.J
      case Filter.ORDER_6        => GnirsController.Filter2Pos.X
      case Filter.X_DISPERSED    => GnirsController.Filter2Pos.XD
      case Filter.H2             => GnirsController.Filter2Pos.H2
      case Filter.H_plus_ND100X  => GnirsController.Filter2Pos.H
      case Filter.H2_plus_ND100X => GnirsController.Filter2Pos.H2
      case Filter.PAH            => GnirsController.Filter2Pos.PAH
      case _                     => GnirsController.Filter2Pos.Open
    }.map(GnirsController.Manual).getOrElse {
      if (xdisp === CrossDispersed.NO)
        GnirsController.Auto
      else GnirsController.Manual(GnirsController.Filter2Pos.XD)
    }

  private def getSlit(slit: SlitWidth): Option[GnirsController.SlitWidth] = slit match {
    case SlitWidth.ACQUISITION  => GnirsController.SlitWidth.Acquisition.some
    case SlitWidth.PINHOLE_1    => GnirsController.SlitWidth.SmallPinhole.some
    case SlitWidth.PINHOLE_3    => GnirsController.SlitWidth.LargePinhole.some
    case SlitWidth.SW_1         => GnirsController.SlitWidth.Slit0_10.some
    case SlitWidth.SW_2         => GnirsController.SlitWidth.Slit0_15.some
    case SlitWidth.SW_3         => GnirsController.SlitWidth.Slit0_20.some
    case SlitWidth.SW_4         => GnirsController.SlitWidth.Slit0_30.some
    case SlitWidth.SW_5         => GnirsController.SlitWidth.Slit0_45.some
    case SlitWidth.SW_6         => GnirsController.SlitWidth.Slit0_68.some
    case SlitWidth.SW_7         => GnirsController.SlitWidth.Slit1_00.some
    case SlitWidth.PUPIL_VIEWER => GnirsController.SlitWidth.PupilViewer.some
    case _                      => None
  }

}
