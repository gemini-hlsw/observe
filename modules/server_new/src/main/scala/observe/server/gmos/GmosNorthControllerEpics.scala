// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.effect.*
import lucuma.core.enums.*
import observe.server.gmos.GmosController.GmosSite
import observe.server.EpicsCodex
import observe.server.EpicsCodex.EncodeEpicsValue
import observe.server.gmos.GmosControllerEpics.ROIValues
import org.typelevel.log4cats.Logger

object GmosNorthEncoders extends GmosControllerEpics.Encoders[GmosSite.North.type] {
  override val filter
    : EpicsCodex.EncodeEpicsValue[Option[GmosSite.Filter[GmosSite.North.type]], (String, String)] =
    EncodeEpicsValue {
      _.map {
        case GmosNorthFilter.GPrime           => ("open1-6", "g_G0301")
        case GmosNorthFilter.RPrime           => ("open1-6", "r_G0303")
        case GmosNorthFilter.IPrime           => ("open1-6", "i_G0302")
        case GmosNorthFilter.ZPrime           => ("open1-6", "z_G0304")
        case GmosNorthFilter.Z                => ("Z_G0322", "open2-8")
        case GmosNorthFilter.Y                => ("Y_G0323", "open2-8")
        case GmosNorthFilter.GG455            => ("GG455_G0305", "open2-8")
        case GmosNorthFilter.OG515            => ("OG515_G0306", "open2-8")
        case GmosNorthFilter.RG610            => ("RG610_G0307", "open2-8")
        case GmosNorthFilter.CaT              => ("CaT_G0309", "open2-8")
        case GmosNorthFilter.Ha               => ("open1-6", "Ha_G0310")
        case GmosNorthFilter.HaC              => ("open1-6", "HaC_G0311")
        case GmosNorthFilter.DS920            => ("open1-6", "DS920_G0312")
        case GmosNorthFilter.SII              => ("SII_G0317", "open2-8")
        case GmosNorthFilter.OIII             => ("OIII_G0318", "open2-8")
        case GmosNorthFilter.OIIIC            => ("OIIIC_G0319", "open2-8")
        case GmosNorthFilter.OVI              => ("open1-6", "OVI_G0345")
        case GmosNorthFilter.OVIC             => ("open1-6", "OVIC_G0346")
        case GmosNorthFilter.HeII             => ("open1-6", "HeII_G0320")
        case GmosNorthFilter.HeIIC            => ("open1-6", "HeIIC_G0321")
        case GmosNorthFilter.HartmannA_RPrime => ("HartmannA_G0313", "r_G0303")
        case GmosNorthFilter.HartmannB_RPrime => ("HartmannB_G0314", "r_G0303")
        case GmosNorthFilter.GPrime_GG455     => ("GG455_G0305", "g_G0301")
        case GmosNorthFilter.GPrime_OG515     => ("OG515_G0306", "g_G0301")
        case GmosNorthFilter.RPrime_RG610     => ("RG610_G0307", "r_G0303")
        case GmosNorthFilter.IPrime_CaT       => ("CaT_G0309", "i_G0302")
        case GmosNorthFilter.ZPrime_CaT       => ("CaT_G0309", "z_G0304")
        case GmosNorthFilter.UPrime           => ("open1-6", "open2-8")
        case GmosNorthFilter.Ri               => ("open1-6", "ri_G0349")

      }
        .getOrElse(("open1-6", "open2-8"))
    }

  override val fpu: EpicsCodex.EncodeEpicsValue[GmosSite.FPU[GmosSite.North.type], String] =
    EncodeEpicsValue {
      case GmosNorthFpu.LongSlit_0_25 => "0.25arcsec"
      case GmosNorthFpu.LongSlit_0_50 => "0.5arcsec"
      case GmosNorthFpu.LongSlit_0_75 => "0.75arcsec"
      case GmosNorthFpu.LongSlit_1_00 => "1.0arcsec"
      case GmosNorthFpu.LongSlit_1_50 => "1.5arcsec"
      case GmosNorthFpu.LongSlit_2_00 => "2.0arcsec"
      case GmosNorthFpu.LongSlit_5_00 => "5.0arcsec"
      case GmosNorthFpu.Ifu2Slits     => "IFU-2"
      case GmosNorthFpu.IfuBlue       => "IFU-B"
      case GmosNorthFpu.IfuRed        => "IFU-R"
      case GmosNorthFpu.Ns0           => "NS0.25arcsec"
      case GmosNorthFpu.Ns1           => "NS0.5arcsec"
      case GmosNorthFpu.Ns2           => "NS0.75arcsec"
      case GmosNorthFpu.Ns3           => "NS1.0arcsec"
      case GmosNorthFpu.Ns4           => "NS1.5arcsec"
      case GmosNorthFpu.Ns5           => "NS2.0arcsec"
    }

  override val stageMode
    : EpicsCodex.EncodeEpicsValue[GmosSite.StageMode[GmosSite.North.type], String] =
    EncodeEpicsValue {
      case GmosNorthStageMode.NoFollow  => "MOVE"
      case GmosNorthStageMode.FollowXyz => "FOLLOW"
      case GmosNorthStageMode.FollowXy  => "FOLLOW-XY"
      case GmosNorthStageMode.FollowZ   => "FOLLOW-Z"
    }

  override val disperser
    : EpicsCodex.EncodeEpicsValue[GmosSite.Grating[GmosSite.North.type], String] =
    EncodeEpicsValue {
      case GmosNorthGrating.B1200_G5301 => "B1200+_G5301"
      case GmosNorthGrating.R831_G5302  => "R831+_G5302"
      case GmosNorthGrating.B600_G5307  => "B600+_G5307"
      case GmosNorthGrating.R600_G5304  => "R600+_G5304"
      case GmosNorthGrating.R400_G5305  => "R400+_G5305"
      case GmosNorthGrating.R150_G5308  => "R150+_G5308"
      case GmosNorthGrating.B600_G5303  => "B600+_G5303"
      case GmosNorthGrating.R150_G5306  => "R150+_G5306"
      case GmosNorthGrating.B480_G5309  => "B480+_G5309"
    }

  override val builtInROI: EncodeEpicsValue[GmosRoi, Option[ROIValues]] = EncodeEpicsValue {
    case GmosRoi.FullFrame       =>
      ROIValues.fromInt(xStart = 1, xSize = 6144, yStart = 1, ySize = 4224)
    case GmosRoi.Ccd2            => ROIValues.fromInt(xStart = 2049, xSize = 2048, yStart = 1, ySize = 4224)
    case GmosRoi.CentralSpectrum =>
      ROIValues.fromInt(xStart = 1, xSize = 6144, yStart = 1625, ySize = 1024)
    case GmosRoi.CentralStamp    =>
      ROIValues.fromInt(xStart = 2923, xSize = 300, yStart = 1987, ySize = 308)
    case _                       => None
  }

  override val autoGain: EncodeEpicsValue[(GmosAmpReadMode, GmosAmpGain), Int] = {
    // gmosAutoGain.lut
    case (GmosAmpReadMode.Slow, GmosAmpGain.Low)  => 4
    case (GmosAmpReadMode.Slow, GmosAmpGain.High) => 0
    case (GmosAmpReadMode.Fast, GmosAmpGain.Low)  => 15
    case (GmosAmpReadMode.Fast, GmosAmpGain.High) => 3
  }

}

object GmosNorthControllerEpics {
  def apply[F[_]: Async: Logger](sys: => GmosEpics[F]): GmosController[F, GmosSite.North.type] = {
    given GmosControllerEpics.Encoders[GmosSite.North.type] = GmosNorthEncoders
    GmosControllerEpics[F, GmosSite.North.type](sys)
  }
}
