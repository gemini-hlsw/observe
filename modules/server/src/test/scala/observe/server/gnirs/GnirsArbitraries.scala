// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gnirs

import edu.gemini.spModel.gemini.gnirs.GNIRSParams
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen
import scala.collection.immutable.ArraySeq

trait GnirsArbitraries {

  given Arbitrary[GNIRSParams.AcquisitionMirror] = Arbitrary(
    Gen.oneOf(ArraySeq.unsafeWrapArray(GNIRSParams.AcquisitionMirror.values()))
  )
  given Cogen[GNIRSParams.AcquisitionMirror]   =
    Cogen[String].contramap(_.displayValue())
  given Arbitrary[GNIRSParams.WollastonPrism]    = Arbitrary(
    Gen.oneOf(ArraySeq.unsafeWrapArray(GNIRSParams.WollastonPrism.values()))
  )
  given Cogen[GNIRSParams.WollastonPrism]      =
    Cogen[String].contramap(_.displayValue())
  given Arbitrary[GNIRSParams.SlitWidth]         = Arbitrary(
    Gen.oneOf(ArraySeq.unsafeWrapArray(GNIRSParams.SlitWidth.values()))
  )
  given Cogen[GNIRSParams.SlitWidth]           =
    Cogen[String].contramap(_.displayValue())
  given Arbitrary[GNIRSParams.CrossDispersed]    = Arbitrary(
    Gen.oneOf(ArraySeq.unsafeWrapArray(GNIRSParams.CrossDispersed.values()))
  )
  given Cogen[GNIRSParams.CrossDispersed]      =
    Cogen[String].contramap(_.displayValue())
  given Arbitrary[GNIRSParams.Decker]            = Arbitrary(
    Gen.oneOf(ArraySeq.unsafeWrapArray(GNIRSParams.Decker.values()))
  )
  given Cogen[GNIRSParams.Decker]              =
    Cogen[String].contramap(_.displayValue())
  given Arbitrary[GNIRSParams.Camera]            = Arbitrary(
    Gen.oneOf(ArraySeq.unsafeWrapArray(GNIRSParams.Camera.values()))
  )
  given Cogen[GNIRSParams.Camera]              =
    Cogen[String].contramap(_.displayValue())
}
