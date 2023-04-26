// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.transition

import edu.gemini.spModel.config2.ItemKey
import edu.gemini.spModel.gemini.gmos.GmosNorthType.{
  DisperserNorth,
  FPUnitNorth,
  FilterNorth,
  StageModeNorth
}
import edu.gemini.spModel.gemini.gmos.GmosSouthType.{
  DisperserSouth,
  FPUnitSouth,
  FilterSouth,
  StageModeSouth
}
import edu.gemini.spModel.gemini.gmos.InstGmosCommon._
import edu.gemini.spModel.gemini.gmos.GmosCommonType._
import edu.gemini.spModel.seqcomp.SeqConfigNames.{INSTRUMENT_KEY, OBSERVE_KEY}
import lucuma.core.enums.{
  GmosAmpCount,
  GmosAmpGain,
  GmosAmpReadMode,
  GmosDtax,
  GmosGratingOrder,
  GmosNorthFilter,
  GmosNorthFpu,
  GmosNorthGrating,
  GmosNorthStageMode,
  GmosRoi,
  GmosSouthFilter,
  GmosSouthFpu,
  GmosSouthGrating,
  GmosSouthStageMode,
  GmosXBinning,
  GmosYBinning
}
import observe.common.ObsQueriesGQL.ObsQuery.{
  GmosFpu,
  GmosGrating,
  GmosInstrumentConfig,
  GmosSite,
  GmosStatic
}
import observe.server.ConfigUtilOps._
import cats.implicits._
import edu.gemini.spModel.obscomp.InstConstants.{EXPOSURE_TIME_PROP, INSTRUMENT_NAME_PROP}
import observe.server.gmos.GmosController.{NorthTypes, SiteDependentTypes, SouthTypes}
import observe.server.gmos.{GmosNorth, GmosSouth}

object GmosTranslator {

  trait GmosSiteConversions[S <: GmosSite] {
    def convertFilter(v: Option[S#Filter]): List[(ItemKey, AnyRef)]

    def convertGrating(v: Option[GmosGrating[S]]): List[(ItemKey, AnyRef)]

    def convertStageMode(v: S#StageMode): List[(ItemKey, AnyRef)]

    def convertFpu(v: Option[GmosFpu[S]]): List[(ItemKey, AnyRef)]

    val instrumentName: String
  }

  private trait TypeTranslations[S <: GmosSite, T <: SiteDependentTypes] {
    def translateFilter(v:     Option[S#Filter]): T#Filter
    def translateGrating(v:    Option[S#Grating]): T#Disperser
    def translateStageMode(v:  S#StageMode): T#GmosStageMode
    def translateBuiltInFpu(v: S#BuiltInFpu): T#FPU
    val customMaskName: T#FPU
    val name: String
  }

  private object GmosSouthTranslations extends TypeTranslations[GmosSite.South, SouthTypes] {
    override def translateFilter(v: Option[GmosSouthFilter]): FilterSouth = v
      .map {
        case GmosSouthFilter.UPrime           => FilterSouth.u_G0332
        case GmosSouthFilter.GPrime           => FilterSouth.g_G0325
        case GmosSouthFilter.RPrime           => FilterSouth.r_G0326
        case GmosSouthFilter.IPrime           => FilterSouth.i_G0327
        case GmosSouthFilter.ZPrime           => FilterSouth.z_G0328
        case GmosSouthFilter.Z                => FilterSouth.Z_G0343
        case GmosSouthFilter.Y                => FilterSouth.Y_G0344
        case GmosSouthFilter.GG455            => FilterSouth.GG455_G0329
        case GmosSouthFilter.OG515            => FilterSouth.OG515_G0330
        case GmosSouthFilter.RG610            => FilterSouth.RG610_G0331
        case GmosSouthFilter.RG780            => FilterSouth.RG780_G0334
        case GmosSouthFilter.CaT              => FilterSouth.CaT_G0333
        case GmosSouthFilter.HartmannA_RPrime => FilterSouth.HartmannA_G0337_r_G0326
        case GmosSouthFilter.HartmannB_RPrime => FilterSouth.HartmannB_G0338_r_G0326
        case GmosSouthFilter.GPrime_GG455     => FilterSouth.g_G0325_GG455_G0329
        case GmosSouthFilter.GPrime_OG515     => FilterSouth.g_G0325_OG515_G0330
        case GmosSouthFilter.RPrime_RG610     => FilterSouth.r_G0326_RG610_G0331
        case GmosSouthFilter.IPrime_RG780     => FilterSouth.i_G0327_RG780_G0334
        case GmosSouthFilter.IPrime_CaT       => FilterSouth.i_G0327_CaT_G0333
        case GmosSouthFilter.ZPrime_CaT       => FilterSouth.z_G0328_CaT_G0333
        case GmosSouthFilter.Ha               => FilterSouth.Ha_G0336
        case GmosSouthFilter.SII              => FilterSouth.SII_G0335
        case GmosSouthFilter.HaC              => FilterSouth.HaC_G0337
        case GmosSouthFilter.OIII             => FilterSouth.OIII_G0338
        case GmosSouthFilter.OIIIC            => FilterSouth.OIII_G0338
        case GmosSouthFilter.HeII             => FilterSouth.HeII_G0340
        case GmosSouthFilter.HeIIC            => FilterSouth.HeIIC_G0341
        case GmosSouthFilter.Lya395           => FilterSouth.Lya395_G0342
      }
      .getOrElse(FilterSouth.NONE)

    override def translateGrating(v: Option[GmosSouthGrating]): DisperserSouth = v
      .map {
        case GmosSouthGrating.B1200_G5321 => DisperserSouth.B1200_G5321
        case GmosSouthGrating.R831_G5322  => DisperserSouth.R831_G5322
        case GmosSouthGrating.B600_G5323  => DisperserSouth.B600_G5323
        case GmosSouthGrating.R600_G5324  => DisperserSouth.R600_G5324
        case GmosSouthGrating.B480_G5327  => DisperserSouth.B480_G5327
        case GmosSouthGrating.R400_G5325  => DisperserSouth.R400_G5325
        case GmosSouthGrating.R150_G5326  => DisperserSouth.R150_G5326
      }
      .getOrElse(DisperserSouth.MIRROR)

    override def translateStageMode(v: GmosSouthStageMode): StageModeSouth = v match {
      case GmosSouthStageMode.NoFollow  => StageModeSouth.NO_FOLLOW
      case GmosSouthStageMode.FollowXyz => StageModeSouth.FOLLOW_XYZ
      case GmosSouthStageMode.FollowXy  => StageModeSouth.FOLLOW_XY
      case GmosSouthStageMode.FollowZ   => StageModeSouth.FOLLOW_Z_ONLY
    }

    override def translateBuiltInFpu(v: GmosSouthFpu): FPUnitSouth = v match {
      case GmosSouthFpu.Bhros         => FPUnitSouth.BHROS
      case GmosSouthFpu.Ns1           => FPUnitSouth.NS_1
      case GmosSouthFpu.Ns2           => FPUnitSouth.NS_2
      case GmosSouthFpu.Ns3           => FPUnitSouth.NS_3
      case GmosSouthFpu.Ns4           => FPUnitSouth.NS_4
      case GmosSouthFpu.Ns5           => FPUnitSouth.NS_5
      case GmosSouthFpu.LongSlit_0_25 => FPUnitSouth.LONGSLIT_1
      case GmosSouthFpu.LongSlit_0_50 => FPUnitSouth.LONGSLIT_2
      case GmosSouthFpu.LongSlit_0_75 => FPUnitSouth.LONGSLIT_3
      case GmosSouthFpu.LongSlit_1_00 => FPUnitSouth.LONGSLIT_4
      case GmosSouthFpu.LongSlit_1_50 => FPUnitSouth.LONGSLIT_5
      case GmosSouthFpu.LongSlit_2_00 => FPUnitSouth.LONGSLIT_6
      case GmosSouthFpu.LongSlit_5_00 => FPUnitSouth.LONGSLIT_7
      case GmosSouthFpu.Ifu2Slits     => FPUnitSouth.IFU_1
      case GmosSouthFpu.IfuBlue       => FPUnitSouth.IFU_2
      case GmosSouthFpu.IfuRed        => FPUnitSouth.IFU_3
      case GmosSouthFpu.IfuNS2Slits   => FPUnitSouth.IFU_N
      case GmosSouthFpu.IfuNSBlue     => FPUnitSouth.IFU_N_B
      case GmosSouthFpu.IfuNSRed      => FPUnitSouth.IFU_N_R
    }

    override val customMaskName: FPUnitSouth = FPUnitSouth.CUSTOM_MASK

    override val name: String = GmosSouth.name
  }

  private object GmosNorthTranslations extends TypeTranslations[GmosSite.North, NorthTypes] {
    override def translateFilter(v: Option[GmosNorthFilter]): FilterNorth = v
      .map {
        case GmosNorthFilter.GPrime           => FilterNorth.g_G0301
        case GmosNorthFilter.RPrime           => FilterNorth.r_G0303
        case GmosNorthFilter.IPrime           => FilterNorth.i_G0302
        case GmosNorthFilter.ZPrime           => FilterNorth.z_G0304
        case GmosNorthFilter.Z                => FilterNorth.Z_G0322
        case GmosNorthFilter.Y                => FilterNorth.Y_G0323
        case GmosNorthFilter.GG455            => FilterNorth.GG455_G0305
        case GmosNorthFilter.OG515            => FilterNorth.OG515_G0306
        case GmosNorthFilter.RG610            => FilterNorth.RG610_G0307
        case GmosNorthFilter.CaT              => FilterNorth.CaT_G0309
        case GmosNorthFilter.Ha               => FilterNorth.Ha_G0310
        case GmosNorthFilter.HaC              => FilterNorth.HaC_G0311
        case GmosNorthFilter.DS920            => FilterNorth.DS920_G0312
        case GmosNorthFilter.SII              => FilterNorth.SII_G0317
        case GmosNorthFilter.OIII             => FilterNorth.OIII_G0318
        case GmosNorthFilter.OIIIC            => FilterNorth.OIIIC_G0319
        case GmosNorthFilter.HeII             => FilterNorth.HeII_G0320
        case GmosNorthFilter.HeIIC            => FilterNorth.HeIIC_G0321
        case GmosNorthFilter.HartmannA_RPrime => FilterNorth.HartmannA_G0313_r_G0303
        case GmosNorthFilter.HartmannB_RPrime => FilterNorth.HartmannB_G0314_r_G0303
        case GmosNorthFilter.GPrime_GG455     => FilterNorth.g_G0301_GG455_G0305
        case GmosNorthFilter.GPrime_OG515     => FilterNorth.g_G0301_OG515_G0306
        case GmosNorthFilter.RPrime_RG610     => FilterNorth.r_G0303_RG610_G0307
        case GmosNorthFilter.IPrime_CaT       => FilterNorth.i_G0302_CaT_G0309
        case GmosNorthFilter.ZPrime_CaT       => FilterNorth.z_G0304_CaT_G0309
        case GmosNorthFilter.UPrime           => FilterNorth.u_G0308
      }
      .getOrElse(FilterNorth.NONE)

    override def translateGrating(v: Option[GmosNorthGrating]): DisperserNorth = v
      .map {
        case GmosNorthGrating.B1200_G5301 => DisperserNorth.B1200_G5301
        case GmosNorthGrating.R831_G5302  => DisperserNorth.R831_G5302
        case GmosNorthGrating.B600_G5303  => DisperserNorth.B600_G5303
        case GmosNorthGrating.B600_G5307  => DisperserNorth.B600_G5307
        case GmosNorthGrating.R600_G5304  => DisperserNorth.R600_G5304
        case GmosNorthGrating.B480_G5309  => DisperserNorth.B480_G5309
        case GmosNorthGrating.R400_G5305  => DisperserNorth.R400_G5305
        case GmosNorthGrating.R150_G5306  => DisperserNorth.R150_G5306
        case GmosNorthGrating.R150_G5308  => DisperserNorth.R150_G5308
      }
      .getOrElse(DisperserNorth.MIRROR)

    override def translateStageMode(v: GmosNorthStageMode): StageModeNorth = v match {
      case GmosNorthStageMode.NoFollow  => StageModeNorth.NO_FOLLOW
      case GmosNorthStageMode.FollowXyz => StageModeNorth.FOLLOW_XYZ
      case GmosNorthStageMode.FollowXy  => StageModeNorth.FOLLOW_XY
      case GmosNorthStageMode.FollowZ   => StageModeNorth.FOLLOW_Z_ONLY
    }

    override def translateBuiltInFpu(v: GmosNorthFpu): FPUnitNorth = v match {
      case GmosNorthFpu.Ns0           => FPUnitNorth.NS_0
      case GmosNorthFpu.Ns1           => FPUnitNorth.NS_1
      case GmosNorthFpu.Ns2           => FPUnitNorth.NS_2
      case GmosNorthFpu.Ns3           => FPUnitNorth.NS_3
      case GmosNorthFpu.Ns4           => FPUnitNorth.NS_4
      case GmosNorthFpu.Ns5           => FPUnitNorth.NS_5
      case GmosNorthFpu.LongSlit_0_25 => FPUnitNorth.LONGSLIT_1
      case GmosNorthFpu.LongSlit_0_50 => FPUnitNorth.LONGSLIT_2
      case GmosNorthFpu.LongSlit_0_75 => FPUnitNorth.LONGSLIT_3
      case GmosNorthFpu.LongSlit_1_00 => FPUnitNorth.LONGSLIT_4
      case GmosNorthFpu.LongSlit_1_50 => FPUnitNorth.LONGSLIT_5
      case GmosNorthFpu.LongSlit_2_00 => FPUnitNorth.LONGSLIT_6
      case GmosNorthFpu.LongSlit_5_00 => FPUnitNorth.LONGSLIT_7
      case GmosNorthFpu.Ifu2Slits     => FPUnitNorth.IFU_1
      case GmosNorthFpu.IfuBlue       => FPUnitNorth.IFU_2
      case GmosNorthFpu.IfuRed        => FPUnitNorth.IFU_3
    }

    override val customMaskName: FPUnitNorth = FPUnitNorth.CUSTOM_MASK

    override val name: String = GmosNorth.name
  }

  private def gmosConversionImpl[S <: GmosSite, T <: SiteDependentTypes](
    t: TypeTranslations[S, T]
  ): GmosSiteConversions[S] =
    new GmosSiteConversions[S] {
      override def convertFilter(v: Option[S#Filter]): List[(ItemKey, AnyRef)] = List(
        (INSTRUMENT_KEY / FILTER_PROP_NAME: ItemKey, t.translateFilter(v).asInstanceOf[AnyRef])
      )

      override def convertGrating(v: Option[GmosGrating[S]]): List[(ItemKey, AnyRef)] = List(
        (INSTRUMENT_KEY / DISPERSER_PROP_NAME,
         t.translateGrating(v.map(_.grating)).asInstanceOf[AnyRef]
        ).some,
        v.map(x =>
          (INSTRUMENT_KEY / DISPERSER_ORDER_PROP,
           x.order match {
             case GmosGratingOrder.Zero => Order.ZERO
             case GmosGratingOrder.One  => Order.ONE
             case GmosGratingOrder.Two  => Order.TWO
           }
          )
        ),
        v.map(x =>
          (INSTRUMENT_KEY / DISPERSER_LAMBDA_PROP,
           java.lang.Double.valueOf(x.wavelength.nanometer.value.toDouble)
          )
        )
      ).flattenOption

      override def convertStageMode(v: S#StageMode): List[(ItemKey, AnyRef)] = List(
        (INSTRUMENT_KEY / STAGE_MODE_PROP, t.translateStageMode(v).asInstanceOf[AnyRef])
      )

      override def convertFpu(v: Option[GmosFpu[S]]): List[(ItemKey, AnyRef)] =
        v.map {
          case GmosFpu(_, Some(b))    =>
            List(
              (INSTRUMENT_KEY / FPU_PROP_NAME, t.translateBuiltInFpu(b).asInstanceOf[AnyRef])
            )
          case GmosFpu(Some(c), None) =>
            List(
              (INSTRUMENT_KEY / FPU_PROP_NAME, t.customMaskName.asInstanceOf[AnyRef]),
              (INSTRUMENT_KEY / FPU_MASK_PROP, c.filename: AnyRef)
            )
          case _                      => List.empty
        }.orEmpty

      override val instrumentName: String = t.name
    }

  implicit val gmosSouthConversions: GmosSiteConversions[GmosSite.South] = gmosConversionImpl(
    GmosSouthTranslations
  )

  implicit val gmosNorthConversions: GmosSiteConversions[GmosSite.North] = gmosConversionImpl(
    GmosNorthTranslations
  )

  private def translateReadMode(v: GmosAmpReadMode): AmpReadMode = v match {
    case GmosAmpReadMode.Slow => AmpReadMode.SLOW
    case GmosAmpReadMode.Fast => AmpReadMode.FAST
  }

  private def translateAmpGain(v: GmosAmpGain): AmpGain = v match {
    case GmosAmpGain.Low  => AmpGain.LOW
    case GmosAmpGain.High => AmpGain.HIGH
  }

  private def translateAmpCount(v: GmosAmpCount): AmpCount = v match {
    case GmosAmpCount.Three  => AmpCount.THREE
    case GmosAmpCount.Six    => AmpCount.SIX
    case GmosAmpCount.Twelve => AmpCount.TWELVE
  }

  private def translateXBinning(v: GmosXBinning): Binning = v match {
    case GmosXBinning.One  => Binning.ONE
    case GmosXBinning.Two  => Binning.TWO
    case GmosXBinning.Four => Binning.FOUR
  }

  private def translateYBinning(v: GmosYBinning): Binning = v match {
    case GmosYBinning.One  => Binning.ONE
    case GmosYBinning.Two  => Binning.TWO
    case GmosYBinning.Four => Binning.FOUR
  }

  private def translateBuiltinROI(v: GmosRoi): BuiltinROI = v match {
    case GmosRoi.FullFrame       => BuiltinROI.FULL_FRAME
    case GmosRoi.Ccd2            => BuiltinROI.CCD2
    case GmosRoi.CentralSpectrum => BuiltinROI.CENTRAL_SPECTRUM
    case GmosRoi.CentralStamp    => BuiltinROI.CENTRAL_STAMP
    case GmosRoi.TopSpectrum     => BuiltinROI.TOP_SPECTRUM
    case GmosRoi.BottomSpectrum  => BuiltinROI.BOTTOM_SPECTRUM
    case GmosRoi.Custom          => BuiltinROI.CUSTOM
  }

  private def translateDTAX(v: GmosDtax): DTAX = v match {
    case GmosDtax.MinusSix   => DTAX.MSIX
    case GmosDtax.MinusFive  => DTAX.MFIVE
    case GmosDtax.MinusFour  => DTAX.MFOUR
    case GmosDtax.MinusThree => DTAX.MTHREE
    case GmosDtax.MinusTwo   => DTAX.MTWO
    case GmosDtax.MinusOne   => DTAX.MONE
    case GmosDtax.Zero       => DTAX.ZERO
    case GmosDtax.One        => DTAX.ONE
    case GmosDtax.Two        => DTAX.TWO
    case GmosDtax.Three      => DTAX.THREE
    case GmosDtax.Four       => DTAX.FOUR
    case GmosDtax.Five       => DTAX.FIVE
    case GmosDtax.Six        => DTAX.SIX
  }

//  private def translateADC(v: GmosAdc): ADC = v match {
//    case GmosAdc.BestStatic => ADC.BEST_STATIC
//    case GmosAdc.Follow => ADC.FOLLOW
//  }

  def instrumentParameters[S <: GmosSite](
    staticConfig: GmosStatic[S],
    instConfig:   GmosInstrumentConfig[S]
  )(implicit conversions: GmosSiteConversions[S]): Map[ItemKey, AnyRef] = {
    val baseParams = List(
      (INSTRUMENT_KEY / INSTRUMENT_NAME_PROP, conversions.instrumentName)
    )

    // TODO: GainSetting is missing
    val defaultGainSetting = 1.0

    val dcParams = List(
      (OBSERVE_KEY / EXPOSURE_TIME_PROP,
       java.lang.Double.valueOf(instConfig.exposure.toMillis / 1000.0): AnyRef
      ),
      (AmpReadMode.KEY, translateReadMode(instConfig.readout.ampReadMode)),
      (INSTRUMENT_KEY / AMP_GAIN_CHOICE_PROP, translateAmpGain(instConfig.readout.ampGain)),
      (INSTRUMENT_KEY / AMP_COUNT_PROP, translateAmpCount(instConfig.readout.ampCount)),
      (INSTRUMENT_KEY / AMP_GAIN_SETTING_PROP, defaultGainSetting.toString),
      (INSTRUMENT_KEY / CCD_X_BIN_PROP, translateXBinning(instConfig.readout.xBin)),
      (INSTRUMENT_KEY / CCD_Y_BIN_PROP, translateYBinning(instConfig.readout.yBin)),
      (INSTRUMENT_KEY / BUILTIN_ROI_PROP, translateBuiltinROI(instConfig.roi))
    )

    val defaultAdc          = ADC.FOLLOW
    val defaultNSStageCount = 2

    val ccParams =
      conversions.convertFilter(instConfig.filter) ++ conversions.convertFpu(instConfig.fpu) ++
        conversions.convertGrating(instConfig.gratingConfig) ++ conversions.convertStageMode(
          staticConfig.stageMode
        ) ++
        List(
          (INSTRUMENT_KEY / DTAX_OFFSET_PROP, translateDTAX(instConfig.dtax)),
          (INSTRUMENT_KEY / ADC_PROP, defaultAdc)
        ) ++
        staticConfig.nodAndShuffle
          .map { x =>
            List(
              (INSTRUMENT_KEY / USE_NS_PROP, java.lang.Boolean.valueOf(true)),
              (INSTRUMENT_KEY / USE_ELECTRONIC_OFFSETTING_PROP,
               java.lang.Boolean.valueOf(x.eOffset.toBoolean)
              ),
              (INSTRUMENT_KEY / NUM_NS_CYCLES_PROP, java.lang.Integer.valueOf(x.shuffleCycles)),
              (INSTRUMENT_KEY / DETECTOR_ROWS_PROP, java.lang.Integer.valueOf(x.shuffleOffset)),
              (INSTRUMENT_KEY / NS_STEP_COUNT_PROP_NAME,
               java.lang.Integer.valueOf(defaultNSStageCount)
              ),
              (INSTRUMENT_KEY / "nsBeamA-p",
               (x.posA.p.toAngle.toMicroarcseconds.toDouble / 1e6).toString
              ),
              (INSTRUMENT_KEY / "nsBeamA-q",
               (x.posA.q.toAngle.toMicroarcseconds.toDouble / 1e6).toString
              ),
              (INSTRUMENT_KEY / "nsBeamB-p",
               (x.posB.p.toAngle.toMicroarcseconds.toDouble / 1e6).toString
              ),
              (INSTRUMENT_KEY / "nsBeamB-q",
               (x.posB.q.toAngle.toMicroarcseconds.toDouble / 1e6).toString
              )
            )
          }
          .getOrElse(
            List(
              (INSTRUMENT_KEY / USE_NS_PROP, java.lang.Boolean.valueOf(false))
            )
          )

    (baseParams ++ dcParams ++ ccParams).toMap
  }

}
