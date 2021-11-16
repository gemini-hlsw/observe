package observe.server.transition

import edu.gemini.spModel.config2.ItemKey
import edu.gemini.spModel.gemini.gmos.GmosNorthType.{DisperserNorth, FPUnitNorth, FilterNorth, StageModeNorth}
import edu.gemini.spModel.gemini.gmos.GmosSouthType.{DisperserSouth, FPUnitSouth, FilterSouth, StageModeSouth}
import edu.gemini.spModel.gemini.gmos.InstGmosCommon._
import edu.gemini.spModel.gemini.gmos.GmosCommonType._
import edu.gemini.spModel.seqcomp.SeqConfigNames.{INSTRUMENT_KEY, OBSERVE_KEY}
import lucuma.core.`enum`.{GmosDisperserOrder, GmosNorthDisperser, GmosNorthFilter, GmosNorthFpu, GmosNorthStageMode, GmosSouthDisperser, GmosSouthFilter, GmosSouthFpu, GmosSouthStageMode}
import observe.common.ObsQueriesGQL.ObsQuery.{GmosFpu, GmosGrating, GmosInstrumentConfig, GmosSite, GmosStatic}
import observe.server.ConfigUtilOps._
import cats.implicits._
import edu.gemini.spModel.obscomp.InstConstants.EXPOSURE_TIME_PROP
import observe.server.gmos.GmosController.{NorthTypes, SiteDependentTypes, SouthTypes}

object GmosTranslator {

  trait GmosSiteConversions[S <: GmosSite] {
    def convertFilter(v: Option[S#Filter]): List[(ItemKey, AnyRef)]

    def convertDisperser(v: Option[GmosGrating[S]]): List[(ItemKey, AnyRef)]

    def convertStageMode(v: S#StageMode): List[(ItemKey, AnyRef)]

    def convertFpu(v: Option[GmosFpu[S]]): List[(ItemKey, AnyRef)]
  }

  private trait TypeTranslations[S <: GmosSite, T <: SiteDependentTypes] {
    def translateFilter(v:     Option[S#Filter]): T#Filter
    def translateDisperser(v:  Option[S#Disperser]): T#Disperser
    def translateStageMode(v:  S#StageMode): T#GmosStageMode
    def translateBuiltInFpu(v: S#BuiltInFpu): T#FPU
    val customMaskName: T#FPU
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

    override def translateDisperser(v: Option[GmosSouthDisperser]): DisperserSouth = v
      .map {
        case GmosSouthDisperser.B1200_G5321 => DisperserSouth.B1200_G5321
        case GmosSouthDisperser.R831_G5322  => DisperserSouth.R831_G5322
        case GmosSouthDisperser.B600_G5323  => DisperserSouth.B600_G5323
        case GmosSouthDisperser.R600_G5324  => DisperserSouth.R600_G5324
        case GmosSouthDisperser.B480_G5327  => DisperserSouth.B480_G5327
        case GmosSouthDisperser.R400_G5325  => DisperserSouth.R400_G5325
        case GmosSouthDisperser.R150_G5326  => DisperserSouth.R150_G5326
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

    override def translateDisperser(v: Option[GmosNorthDisperser]): DisperserNorth = v
      .map {
        case GmosNorthDisperser.B1200_G5301 => DisperserNorth.B1200_G5301
        case GmosNorthDisperser.R831_G5302  => DisperserNorth.R831_G5302
        case GmosNorthDisperser.B600_G5303  => DisperserNorth.B600_G5303
        case GmosNorthDisperser.B600_G5307  => DisperserNorth.B600_G5307
        case GmosNorthDisperser.R600_G5304  => DisperserNorth.R600_G5304
        case GmosNorthDisperser.B480_G5309  => DisperserNorth.B480_G5309
        case GmosNorthDisperser.R400_G5305  => DisperserNorth.R400_G5305
        case GmosNorthDisperser.R150_G5306  => DisperserNorth.R150_G5306
        case GmosNorthDisperser.R150_G5308  => DisperserNorth.R150_G5308
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
  }

  private def gmosConversionImpl[S <: GmosSite, T <: SiteDependentTypes](
    t: TypeTranslations[S, T]
  ): GmosSiteConversions[S] =
    new GmosSiteConversions[S] {
      override def convertFilter(v: Option[S#Filter]): List[(ItemKey, AnyRef)] = List(
        (INSTRUMENT_KEY / FILTER_PROP_NAME: ItemKey, t.translateFilter(v).asInstanceOf[AnyRef])
      )

      override def convertDisperser(v: Option[GmosGrating[S]]): List[(ItemKey, AnyRef)] = List(
        (INSTRUMENT_KEY / DISPERSER_PROP_NAME,
         t.translateDisperser(v.map(_.disperser)).asInstanceOf[AnyRef]
        ).some,
        v.map(x =>
          (INSTRUMENT_KEY / DISPERSER_ORDER_PROP,
           x.order match {
             case GmosDisperserOrder.Zero => Order.ZERO
             case GmosDisperserOrder.One  => Order.ONE
             case GmosDisperserOrder.Two  => Order.TWO
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
          case GmosFpu.GmosBuiltinFpu(builtin)     =>
            List(
              (INSTRUMENT_KEY / FPU_PROP_NAME, t.translateBuiltInFpu(builtin).asInstanceOf[AnyRef])
            )
          case GmosFpu.GmosCustomMask(filename, _) =>
            List(
              (INSTRUMENT_KEY / FPU_PROP_NAME, t.customMaskName.asInstanceOf[AnyRef]),
              (INSTRUMENT_KEY / FPU_MASK_PROP, filename: AnyRef)
            )
        }.orEmpty
    }

  implicit val gmosSouthConversions: GmosSiteConversions[GmosSite.South] = gmosConversionImpl(
    GmosSouthTranslations
  )

  implicit val gmosNorthConversions: GmosSiteConversions[GmosSite.North] = gmosConversionImpl(
    GmosNorthTranslations
  )

  def instrumentParameters[S <: GmosSite](staticConfig: GmosStatic[S], instConfig: GmosInstrumentConfig[S])(implicit conversions: GmosSiteConversions[S]): Map[ItemKey, AnyRef] = {
    val dcParams = List(
      (OBSERVE_KEY / EXPOSURE_TIME_PROP, java.lang.Double.valueOf(instConfig.exposure.toMillis / 1000.0): AnyRef)
    )

    val ccParams = conversions.convertFilter(instConfig.filter) ++ conversions.convertFpu(instConfig.fpu) ++ conversions.convertDisperser(instConfig.grating) ++ conversions.convertStageMode(staticConfig.stageMode)

    (dcParams ++ ccParams).toMap
  }

}
