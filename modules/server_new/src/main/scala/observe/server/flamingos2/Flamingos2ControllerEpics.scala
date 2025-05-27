// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.flamingos2

import cats.data.StateT
import cats.effect.Async
import cats.syntax.all.*
import lucuma.core.enums.Flamingos2Decker
import lucuma.core.enums.Flamingos2Filter
import lucuma.core.enums.Flamingos2LyotWheel
import lucuma.core.enums.Flamingos2ReadoutMode
import lucuma.core.enums.Flamingos2WindowCover
import lucuma.core.util.TimeSpan
import observe.model.ObserveStage
import observe.model.dhs.ImageFileId
import observe.model.enums.ObserveCommandResult
import observe.server.EpicsCodex.*
import observe.server.ObsProgress
import observe.server.Progress
import observe.server.ProgressUtil
import observe.server.RemainingTime
import observe.server.flamingos2.Flamingos2Controller.*
import org.typelevel.log4cats.Logger

trait Flamingos2Encoders {
  given EncodeEpicsValue[Flamingos2ReadoutMode, String] =
    EncodeEpicsValue {
      case Flamingos2ReadoutMode.Science     => "SCI"
      case Flamingos2ReadoutMode.Engineering => "ENG"
    }

  given EncodeEpicsValue[BiasMode, String] = EncodeEpicsValue {
    case Flamingos2Decker.Imaging  => "Imaging"
    case Flamingos2Decker.LongSlit => "Long_Slit"
    case Flamingos2Decker.MOS      => "Mos"
  }

  given EncodeEpicsValue[Flamingos2WindowCover, String] = EncodeEpicsValue {
    case Flamingos2WindowCover.Open  => "Open"
    case Flamingos2WindowCover.Close => "Closed"
  }

  given EncodeEpicsValue[Flamingos2Decker, String] = EncodeEpicsValue {
    case Flamingos2Decker.Imaging  => "Open"
    case Flamingos2Decker.LongSlit => "Long_Slit"
    case Flamingos2Decker.MOS      => "Mos"
  }

  given EncodeEpicsValue[FocalPlaneUnit, (String, String)] =
    EncodeEpicsValue {
      case FocalPlaneUnit.Open        => ("Open", "null")
      case FocalPlaneUnit.GridSub1Pix => ("sub1-pix_grid", "null")
      case FocalPlaneUnit.Grid2Pix    => ("2-pix_grid", "null")
      case FocalPlaneUnit.Slit1Pix    => ("1pix-slit", "null")
      case FocalPlaneUnit.Slit2Pix    => ("2pix-slit", "null")
      case FocalPlaneUnit.Slit3Pix    => ("3pix-slit", "null")
      case FocalPlaneUnit.Slit4Pix    => ("4pix-slit", "null")
      case FocalPlaneUnit.Slit6Pix    => ("6pix-slit", "null")
      case FocalPlaneUnit.Slit8Pix    => ("8pix-slit", "null")
      case FocalPlaneUnit.Custom(s)   => ("null", s)
    }

  given EncodeEpicsValue[Flamingos2Filter, Option[String]] =
    EncodeEpicsValue.applyO {
      case Flamingos2Filter.Y      => "YJH_G0818"
      case Flamingos2Filter.J      => "J_G0802"
      case Flamingos2Filter.H      => "H_G0803"
      case Flamingos2Filter.JH     => "JH_G0816"
      case Flamingos2Filter.HK     => "HK_G0817"
      case Flamingos2Filter.JLow   => "J-lo_G0801"
      case Flamingos2Filter.KLong  => "K-long_G0812"
      case Flamingos2Filter.KShort => "Ks_G0804"
      case Flamingos2Filter.KBlue  => "K-blue_G0814"
      case Flamingos2Filter.KRed   => "K-red_G0815"
    }

  implicit val encodeLyotPosition: EncodeEpicsValue[Flamingos2LyotWheel, String] =
    EncodeEpicsValue {
      case Flamingos2LyotWheel.F16       => "f/16_G5830"
      case Flamingos2LyotWheel.GemsOver  => "GEMS_over_G5836"
      case Flamingos2LyotWheel.GemsUnder => "GEMS_under_G5835"
      case Flamingos2LyotWheel.HartmannA => "Hart1_G5833"
      case Flamingos2LyotWheel.HartmannB => "Hart2_G5834"
    }

  implicit val encodeGrismPosition: EncodeEpicsValue[Grism, String] = EncodeEpicsValue {
    case Grism.Open    => "Open"
    case Grism.R1200HK => "HK_G5802"
    case Grism.R1200JH => "JH_G5801"
    case Grism.R3000   => "R3K_G5803"
    case Grism.Dark    => "DK_G5804"
  }

}

object Flamingos2ControllerEpics extends Flamingos2Encoders {

  val ReadoutTimeout: TimeSpan = TimeSpan.fromSeconds(120).get
  val DefaultTimeout: TimeSpan = TimeSpan.fromSeconds(60).get
  val ConfigTimeout: TimeSpan  = TimeSpan.fromSeconds(400).get

  def apply[F[_]: Async](
    sys: => Flamingos2Epics[F]
  )(implicit L: Logger[F]): Flamingos2Controller[F] = new Flamingos2Controller[F] {

    private def setDCConfig(dc: DCConfig): F[Unit] = for {
      _ <- sys.dcConfigCmd.setExposureTime(dc.exposureTime.toSeconds.toDouble)
      _ <- sys.dcConfigCmd.setNumReads(dc.reads.reads)
      _ <- sys.dcConfigCmd.setReadoutMode(encode(dc.readoutMode))
      _ <- sys.dcConfigCmd.setBiasMode(encode(dc.decker))
    } yield ()

    private def filterAndLyot(cc: CCConfig): (Option[String], String) =
      if (filterInLyotWheel(cc.filter)) {
        (
          "Open".some,
          encode(cc.filter).getOrElse(encode(cc.lyotWheel))
        )
      } else
        (
          encode(cc.filter),
          encode(cc.lyotWheel)
        )

    private def setCCConfig(cc: CCConfig): F[Unit] = {
      val fpu                      = encode(cc.fpu)
      val (filterValue, lyotValue) = filterAndLyot(cc)
      for {
        _ <- sys.configCmd.setWindowCover(encode(cc.windowCover))
        _ <- sys.configCmd.setDecker(encode(cc.decker))
        _ <- sys.configCmd.setMOS(fpu._1)
        _ <- sys.configCmd.setMask(fpu._2)
        _ <- filterValue.map(sys.configCmd.setFilter).getOrElse(Async[F].unit)
        _ <- sys.configCmd.setLyot(lyotValue)
        _ <- sys.configCmd.setGrism(encode(cc.grism))
      } yield ()
    }

    override def applyConfig(config: Flamingos2Config): F[Unit] = for {
      _ <- L.debug("Start Flamingos2 configuration")
      _ <- setDCConfig(config.dc)
      _ <- setCCConfig(config.cc)
      _ <- sys.post(ConfigTimeout)
      _ <- L.debug("Completed Flamingos2 configuration")
    } yield ()

    override def observe(fileId: ImageFileId, expTime: TimeSpan): F[ObserveCommandResult] = for {
      _ <- L.debug(s"Send observe to Flamingos2, file id $fileId")
      _ <- sys.observeCmd.setLabel(fileId.value)
      _ <-
        sys.observeCmd.post(expTime +| ReadoutTimeout)
      _ <- L.debug("Completed Flamingos2 observe")
    } yield ObserveCommandResult.Success

    override def endObserve: F[Unit] = for {
      _ <- L.debug("Send endObserve to Flamingos2")
      _ <- sys.endObserveCmd.mark
      _ <- sys.endObserveCmd.post(DefaultTimeout)
      _ <- L.debug("endObserve sent to Flamingos2")
    } yield ()

    override def observeProgress(total: TimeSpan): fs2.Stream[F, Progress] = {
      val s: TimeSpan => fs2.Stream[F, Progress] =
        ProgressUtil.fromStateTOption[F, TimeSpan](_ =>
          StateT[F, TimeSpan, Option[Progress]] { (st: TimeSpan) =>
            val max: TimeSpan                    = if (total >= st) total else st
            val progress: F[Option[ObsProgress]] =
              for {
                obst <- sys.observeState
                rem  <- sys.countdown
                v    <- (sys.dcIsPreparing, sys.dcIsAcquiring, sys.dcIsReadingOut).mapN(
                          ObserveStage.fromBooleans
                        )
              } yield
                if (obst.isBusy)
                  ObsProgress(max, RemainingTime(TimeSpan.fromSeconds(rem).get), v).some
                else none
            progress.map(p => (max, p))
          }
        )
      s(total)
        .dropWhile(_.remaining.value.isZero) // drop leading zeros
        .takeThrough: // drop all tailing zeros but the first one
          _.remaining.value.toMicroseconds > 0
    }
  }

  def filterInLyotWheel(filter: Flamingos2Filter): Boolean = filter match {
    case Flamingos2Filter.Y | Flamingos2Filter.JLow => true
    case _                                          => false
  }
}
