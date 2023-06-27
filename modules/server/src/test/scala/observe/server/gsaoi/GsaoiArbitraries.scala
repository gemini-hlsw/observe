// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gsaoi

import edu.gemini.spModel.gemini.gsaoi.Gsaoi.ReadMode
import edu.gemini.spModel.gemini.gsaoi.Gsaoi.Roi
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen.*
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen
import shapeless.tag
import observe.server.gsaoi.GsaoiController.*
import observe.model.arb.ArbTime.*

trait GsaoiArbitraries {

  given Arbitrary[WindowCover] =
    Arbitrary(Gen.oneOf(WindowCover.Closed, WindowCover.Opened))
  given Cogen[WindowCover]     =
    Cogen[String].contramap(_.productPrefix)

  given Arbitrary[DCConfig] =
    Arbitrary {
      for {
        readMode           <- arbitrary[ReadMode]
        roi                <- arbitrary[Roi]
        coadds             <- arbitrary[Int]
        exposureTime       <- arbitrary[ExposureTime]
        numberOfFowSamples <- arbitrary[Int]
      } yield DCConfig(readMode,
                       roi,
                       tag[CoaddsI][Int](coadds),
                       exposureTime,
                       tag[NumberOfFowSamplesI][Int](numberOfFowSamples)
      )
    }

  given Cogen[ReadMode] =
    Cogen[String].contramap(_.displayValue())

  given Cogen[Roi] =
    Cogen[String].contramap(_.displayValue())

  given Cogen[DCConfig] =
    Cogen[(ReadMode, Roi, Int, ExposureTime, Int)].contramap(x =>
      (x.readMode, x.roi, x.coadds, x.exposureTime, x.numberOfFowSamples)
    )

  given Arbitrary[CCConfig] =
    Arbitrary {
      for {
        filter       <- arbitrary[Filter]
        odgwSize     <- arbitrary[OdgwSize]
        utilityWheel <- arbitrary[UtilityWheel]
        windowCover  <- arbitrary[WindowCover]
      } yield CCConfig(filter, odgwSize, utilityWheel, windowCover)
    }

  given Cogen[Filter] =
    Cogen[String].contramap(_.displayValue())

  given Cogen[OdgwSize] =
    Cogen[String].contramap(_.displayValue())

  given Cogen[UtilityWheel] =
    Cogen[String].contramap(_.displayValue())

  given Cogen[CCConfig] =
    Cogen[(Filter, OdgwSize, UtilityWheel, WindowCover)].contramap(x =>
      (x.filter, x.odgwSize, x.utilityWheel, x.windowCover)
    )
}
