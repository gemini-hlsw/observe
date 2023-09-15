// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.{Arbitrary, Cogen}
import org.scalacheck.Arbitrary.*
import squants.Length
import squants.space.LengthConversions.*

trait ArbLength {

  given Arbitrary[Length] = Arbitrary {
    arbitrary[Double].map(_.meters)
  }

  given Cogen[Length] = Cogen[Double].contramap(_.toMeters)

}

object ArbLength extends ArbLength
