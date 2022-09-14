// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.transition

import edu.gemini.spModel.config2.ItemKey
import edu.gemini.spModel.gemini.gmos.InstGmosCommon._
import edu.gemini.spModel.gemini.gmos.GmosCommonType._
import edu.gemini.spModel.seqcomp.SeqConfigNames.{ INSTRUMENT_KEY, OBSERVE_KEY }
import lucuma.core.enums.{
  GmosAmpCount,
  GmosAmpGain,
  GmosAmpReadMode,
  GmosDtax,
  GmosGratingOrder,
  GmosRoi,
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
import edu.gemini.spModel.obscomp.InstConstants.{ EXPOSURE_TIME_PROP, INSTRUMENT_NAME_PROP }
import observe.server.gmos.{ GmosNorth, GmosSouth }

object GmosTranslator {

  trait GmosSiteConversions[S <: GmosSite] {
    def convertFilter(v: Option[S#Filter]): List[(ItemKey, AnyRef)]

    def convertGrating(v: Option[GmosGrating[S]]): List[(ItemKey, AnyRef)]

    def convertStageMode(v: S#StageMode): List[(ItemKey, AnyRef)]

    def convertFpu(v: Option[GmosFpu[S]]): List[(ItemKey, AnyRef)]

    val instrumentName: String
  }

//  private trait TypeTranslations[S <: GmosSite, T <: SiteDependentTypes] {
//    val name: String
//  }
//
//  private object GmosSouthTranslations extends TypeTranslations[GmosSite.South, GmosSite.South] {
//    override val name: String = GmosSouth.name
//  }
//
//  private object GmosNorthTranslations extends TypeTranslations[GmosSite.North, GmosSite.North] {
//    override val name: String = GmosNorth.name
//  }
//
//  private def gmosConversionImpl[S <: GmosSite, T <: SiteDependentTypes](
//    t: TypeTranslations[S, T]
//  ): GmosSiteConversions[S] =
//    new GmosSiteConversions[S] {
//      override def convertFilter(v: Option[S#Filter]): List[(ItemKey, AnyRef)] = List(
//        (INSTRUMENT_KEY / FILTER_PROP_NAME: ItemKey, v.asInstanceOf[AnyRef])
//      )
//
//      override def convertGrating(v: Option[GmosGrating[S]]): List[(ItemKey, AnyRef)] = List(
//        (INSTRUMENT_KEY / DISPERSER_PROP_NAME, v.asInstanceOf[AnyRef]).some,
//        v.map(x =>
//          (INSTRUMENT_KEY / DISPERSER_ORDER_PROP,
//           x.order match {
//             case GmosGratingOrder.Zero => Order.ZERO
//             case GmosGratingOrder.One  => Order.ONE
//             case GmosGratingOrder.Two  => Order.TWO
//           }
//          )
//        ),
//        v.map(x =>
//          (INSTRUMENT_KEY / DISPERSER_LAMBDA_PROP,
//           java.lang.Double.valueOf(x.wavelength.nanometer.value.toDouble)
//          )
//        )
//      ).flattenOption
//
//      override def convertStageMode(v: S#StageMode): List[(ItemKey, AnyRef)] = List(
//        (INSTRUMENT_KEY / STAGE_MODE_PROP, v.asInstanceOf[AnyRef])
//      )
//
//      override def convertFpu(v: Option[GmosFpu[S]]): List[(ItemKey, AnyRef)] =
//        v.map {
//          case GmosFpu(_, Some(b))    =>
//            List(
//              (INSTRUMENT_KEY / FPU_PROP_NAME, b.asInstanceOf[AnyRef])
//            )
//          case GmosFpu(Some(c), None) =>
//            List(
//              (INSTRUMENT_KEY / FPU_MASK_PROP, c.filename: AnyRef)
//            )
//          case _                      => List.empty
//        }.orEmpty
//
//      override val instrumentName: String = t.name
//    }
//
//  implicit val gmosSouthConversions: GmosSiteConversions[GmosSite.South] = gmosConversionImpl(
//    GmosSouthTranslations
//  )
//
//  implicit val gmosNorthConversions: GmosSiteConversions[GmosSite.North] = gmosConversionImpl(
//    GmosNorthTranslations
//  )
//
//  private def translateReadMode(v: GmosAmpReadMode): AmpReadMode = v match {
//    case GmosAmpReadMode.Slow => AmpReadMode.SLOW
//    case GmosAmpReadMode.Fast => AmpReadMode.FAST
//  }
//
//  private def translateAmpGain(v: GmosAmpGain): AmpGain = v match {
//    case GmosAmpGain.Low  => AmpGain.LOW
//    case GmosAmpGain.High => AmpGain.HIGH
//  }
//
//  private def translateAmpCount(v: GmosAmpCount): AmpCount = v match {
//    case GmosAmpCount.Three  => AmpCount.THREE
//    case GmosAmpCount.Six    => AmpCount.SIX
//    case GmosAmpCount.Twelve => AmpCount.TWELVE
//  }
//
//  private def translateXBinning(v: GmosXBinning): Binning = v match {
//    case GmosXBinning.One  => Binning.ONE
//    case GmosXBinning.Two  => Binning.TWO
//    case GmosXBinning.Four => Binning.FOUR
//  }
//
//  private def translateYBinning(v: GmosYBinning): Binning = v match {
//    case GmosYBinning.One  => Binning.ONE
//    case GmosYBinning.Two  => Binning.TWO
//    case GmosYBinning.Four => Binning.FOUR
//  }
//
//  private def translateBuiltinROI(v: GmosRoi): BuiltinROI = v match {
//    case GmosRoi.FullFrame       => BuiltinROI.FULL_FRAME
//    case GmosRoi.Ccd2            => BuiltinROI.CCD2
//    case GmosRoi.CentralSpectrum => BuiltinROI.CENTRAL_SPECTRUM
//    case GmosRoi.CentralStamp    => BuiltinROI.CENTRAL_STAMP
//    case GmosRoi.TopSpectrum     => BuiltinROI.TOP_SPECTRUM
//    case GmosRoi.BottomSpectrum  => BuiltinROI.BOTTOM_SPECTRUM
//    case GmosRoi.Custom          => BuiltinROI.CUSTOM
//  }
//
//  private def translateDTAX(v: GmosDtax): DTAX = v match {
//    case GmosDtax.MinusSix   => DTAX.MSIX
//    case GmosDtax.MinusFive  => DTAX.MFIVE
//    case GmosDtax.MinusFour  => DTAX.MFOUR
//    case GmosDtax.MinusThree => DTAX.MTHREE
//    case GmosDtax.MinusTwo   => DTAX.MTWO
//    case GmosDtax.MinusOne   => DTAX.MONE
//    case GmosDtax.Zero       => DTAX.ZERO
//    case GmosDtax.One        => DTAX.ONE
//    case GmosDtax.Two        => DTAX.TWO
//    case GmosDtax.Three      => DTAX.THREE
//    case GmosDtax.Four       => DTAX.FOUR
//    case GmosDtax.Five       => DTAX.FIVE
//    case GmosDtax.Six        => DTAX.SIX
//  }
//
////  private def translateADC(v: GmosAdc): ADC = v match {
////    case GmosAdc.BestStatic => ADC.BEST_STATIC
////    case GmosAdc.Follow => ADC.FOLLOW
////  }
//
//  def instrumentParameters[S <: GmosSite](
//    staticConfig:         GmosStatic[S],
//    instConfig:           GmosInstrumentConfig[S]
//  )(implicit conversions: GmosSiteConversions[S]): Map[ItemKey, AnyRef] = {
//    val baseParams = List(
//      (INSTRUMENT_KEY / INSTRUMENT_NAME_PROP, conversions.instrumentName)
//    )
//
//    // TODO: GainSetting is missing
//    val defaultGainSetting = 1.0
//
//    val dcParams = List(
//      (OBSERVE_KEY / EXPOSURE_TIME_PROP,
//       java.lang.Double.valueOf(instConfig.exposure.toMillis / 1000.0): AnyRef
//      ),
//      (AmpReadMode.KEY, translateReadMode(instConfig.readout.ampReadMode)),
//      (INSTRUMENT_KEY / AMP_GAIN_CHOICE_PROP, translateAmpGain(instConfig.readout.ampGain)),
//      (INSTRUMENT_KEY / AMP_COUNT_PROP, translateAmpCount(instConfig.readout.ampCount)),
//      (INSTRUMENT_KEY / AMP_GAIN_SETTING_PROP, defaultGainSetting.toString),
//      (INSTRUMENT_KEY / CCD_X_BIN_PROP, translateXBinning(instConfig.readout.xBin)),
//      (INSTRUMENT_KEY / CCD_Y_BIN_PROP, translateYBinning(instConfig.readout.yBin)),
//      (INSTRUMENT_KEY / BUILTIN_ROI_PROP, translateBuiltinROI(instConfig.roi))
//    )
//
//    val defaultAdc          = ADC.FOLLOW
//    val defaultNSStageCount = 2
//
//    val ccParams =
//      conversions.convertFilter(instConfig.filter) ++ conversions.convertFpu(instConfig.fpu) ++
//        conversions.convertGrating(instConfig.gratingConfig) ++ conversions.convertStageMode(
//          staticConfig.stageMode
//        ) ++
//        List(
//          (INSTRUMENT_KEY / DTAX_OFFSET_PROP, translateDTAX(instConfig.dtax)),
//          (INSTRUMENT_KEY / ADC_PROP, defaultAdc)
//        ) ++
//        staticConfig.nodAndShuffle
//          .map { x =>
//            List(
//              (INSTRUMENT_KEY / USE_NS_PROP, java.lang.Boolean.valueOf(true)),
//              (INSTRUMENT_KEY / USE_ELECTRONIC_OFFSETTING_PROP,
//               java.lang.Boolean.valueOf(x.eOffset.toBoolean)
//              ),
//              (INSTRUMENT_KEY / NUM_NS_CYCLES_PROP, java.lang.Integer.valueOf(x.shuffleCycles)),
//              (INSTRUMENT_KEY / DETECTOR_ROWS_PROP, java.lang.Integer.valueOf(x.shuffleOffset)),
//              (INSTRUMENT_KEY / NS_STEP_COUNT_PROP_NAME,
//               java.lang.Integer.valueOf(defaultNSStageCount)
//              ),
//              (INSTRUMENT_KEY / "nsBeamA-p",
//               (x.posA.p.toAngle.toMicroarcseconds.toDouble / 1e6).toString
//              ),
//              (INSTRUMENT_KEY / "nsBeamA-q",
//               (x.posA.q.toAngle.toMicroarcseconds.toDouble / 1e6).toString
//              ),
//              (INSTRUMENT_KEY / "nsBeamB-p",
//               (x.posB.p.toAngle.toMicroarcseconds.toDouble / 1e6).toString
//              ),
//              (INSTRUMENT_KEY / "nsBeamB-q",
//               (x.posB.q.toAngle.toMicroarcseconds.toDouble / 1e6).toString
//              )
//            )
//          }
//          .getOrElse(
//            List(
//              (INSTRUMENT_KEY / USE_NS_PROP, java.lang.Boolean.valueOf(false))
//            )
//          )
//
//    (baseParams ++ dcParams ++ ccParams).toMap
//  }

}
