// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gpi

import edu.gemini.spModel.gemini.gpi.Gpi.{Apodizer => LegacyApodizer}
import edu.gemini.spModel.gemini.gpi.Gpi.{Adc => LegacyAdc}
import edu.gemini.spModel.gemini.gpi.Gpi.{ArtificialSource => LegacyArtificialSource}
import edu.gemini.spModel.gemini.gpi.Gpi.{Disperser => LegacyDisperser}
import edu.gemini.spModel.gemini.gpi.Gpi.{FPM => LegacyFPM}
import edu.gemini.spModel.gemini.gpi.Gpi.{Filter => LegacyFilter}
import edu.gemini.spModel.gemini.gpi.Gpi.{Lyot => LegacyLyot}
import edu.gemini.spModel.gemini.gpi.Gpi.{ObservingMode => LegacyObservingMode}
import edu.gemini.spModel.gemini.gpi.Gpi.{PupilCamera => LegacyPupilCamera}
import edu.gemini.spModel.gemini.gpi.Gpi.{Shutter => LegacyShutter}
import lucuma.core.enums.GpiReadMode
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.arb.ArbTime
import org.scalacheck.Arbitrary.*
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen
import scala.concurrent.duration.Duration

trait GpiArbitraries extends ArbTime {

  given Arbitrary[AOFlags] = Arbitrary {
    for {
      useAo    <- arbitrary[Boolean]
      useCal   <- arbitrary[Boolean]
      aoOpt    <- arbitrary[Boolean]
      alignFpm <- arbitrary[Boolean]
      magH     <- arbitrary[Double]
      magI     <- arbitrary[Double]
    } yield AOFlags(useAo, useCal, aoOpt, alignFpm, magH, magI)
  }
  given Cogen[AOFlags]     =
    Cogen[(Boolean, Boolean, Boolean, Boolean)]
      .contramap(x => (x.useAo, x.useCal, x.aoOptimize, x.alignFpm))

  given Arbitrary[ArtificialSources]  =
    Arbitrary {
      for {
        ir  <- arbitrary[LegacyArtificialSource]
        vis <- arbitrary[LegacyArtificialSource]
        sc  <- arbitrary[LegacyArtificialSource]
        att <- arbitrary[Double]
      } yield ArtificialSources(ir, vis, sc, att)
    }
  given Cogen[LegacyArtificialSource] =
    Cogen[String].contramap(_.displayValue)
  given Cogen[ArtificialSources]      =
    Cogen[(LegacyArtificialSource, LegacyArtificialSource, LegacyArtificialSource, Double)]
      .contramap(x => (x.ir, x.vis, x.sc, x.attenuation))

  given Arbitrary[Shutters] = Arbitrary {
    for {
      ent <- arbitrary[LegacyShutter]
      cal <- arbitrary[LegacyShutter]
      sci <- arbitrary[LegacyShutter]
      ref <- arbitrary[LegacyShutter]
    } yield Shutters(ent, cal, sci, ref)
  }

  given Cogen[LegacyShutter] =
    Cogen[String].contramap(_.displayValue)
  given Cogen[Shutters]      =
    Cogen[(LegacyShutter, LegacyShutter, LegacyShutter, LegacyShutter)]
      .contramap(x =>
        (x.entranceShutter, x.calEntranceShutter, x.calScienceShutter, x.calReferenceShutter)
      )

  given Arbitrary[NonStandardModeParams] =
    Arbitrary {
      for {
        apo <- arbitrary[LegacyApodizer]
        fpm <- arbitrary[LegacyFPM]
        lyo <- arbitrary[LegacyLyot]
        fil <- arbitrary[LegacyFilter]
      } yield NonStandardModeParams(apo, fpm, lyo, fil)
    }

  given Cogen[LegacyApodizer]        =
    Cogen[String].contramap(_.displayValue)
  given Cogen[LegacyFPM]             =
    Cogen[String].contramap(_.displayValue)
  given Cogen[LegacyLyot]            =
    Cogen[String].contramap(_.displayValue)
  given Cogen[LegacyFilter]          =
    Cogen[String].contramap(_.displayValue)
  given Cogen[NonStandardModeParams] =
    Cogen[(LegacyApodizer, LegacyFPM, LegacyLyot, LegacyFilter)]
      .contramap(x => (x.apodizer, x.fpm, x.lyot, x.filter))

  given Arbitrary[ReadoutArea] =
    Arbitrary {
      for {
        startX <- Gen.choose(ReadoutArea.MinValue, ReadoutArea.MaxValue)
        startY <- Gen.choose(ReadoutArea.MinValue, ReadoutArea.MaxValue)
        endX   <- Gen.choose(ReadoutArea.MinValue, ReadoutArea.MaxValue)
        endY   <- Gen.choose(ReadoutArea.MinValue, ReadoutArea.MaxValue)
      } yield ReadoutArea.fromValues(startX, startY, endX, endY).getOrElse(ReadoutArea.DefaultArea)
    }
  given Cogen[ReadoutArea]     =
    Cogen[(Int, Int, Int, Int)].contramap(x => (x.startX, x.startY, x.endX, x.endY))

  given Arbitrary[RegularGpiConfig] = Arbitrary {
    for {
      adc   <- arbitrary[LegacyAdc]
      exp   <- arbitrary[Duration]
      coa   <- Gen.posNum[Int]
      readM <- arbitrary[GpiReadMode]
      area  <- arbitrary[ReadoutArea]
      obsM  <- arbitrary[Either[LegacyObservingMode, NonStandardModeParams]]
      disp  <- arbitrary[LegacyDisperser]
      dispA <- arbitrary[Double]
      shut  <- arbitrary[Shutters]
      asu   <- arbitrary[ArtificialSources]
      pc    <- arbitrary[LegacyPupilCamera]
      ao    <- arbitrary[AOFlags]
    } yield RegularGpiConfig(adc, exp, coa, readM, area, obsM, disp, dispA, shut, asu, pc, ao)
  }

  given Cogen[LegacyAdc]           =
    Cogen[String].contramap(_.displayValue)
  given Cogen[LegacyObservingMode] =
    Cogen[String].contramap(_.displayValue)
  given Cogen[LegacyPupilCamera]   =
    Cogen[String].contramap(_.displayValue)
  given Cogen[RegularGpiConfig]    =
    Cogen[
      (
        LegacyAdc,
        Duration,
        Int,
        GpiReadMode,
        ReadoutArea,
        Either[LegacyObservingMode, NonStandardModeParams],
        Shutters,
        ArtificialSources,
        LegacyPupilCamera,
        AOFlags
      )
    ]
      .contramap(x =>
        (x.adc, x.expTime, x.coAdds, x.readMode, x.area, x.mode, x.shutters, x.asu, x.pc, x.aoFlags)
      )
}
