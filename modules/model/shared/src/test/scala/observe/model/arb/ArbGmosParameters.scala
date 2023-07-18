// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import observe.model.GmosParameters.*

trait ArbGmosParameters {
  given gmosNsPairsArb: Arbitrary[NsPairs]   =
    Arbitrary(arbitrary[Int].map(NsPairs.apply(_)))
  given gmosNsPairsCogen: Cogen[NsPairs]     =
    Cogen[Int].contramap(_.value)
  given gmosNsRowsArb: Arbitrary[NsRows]     =
    Arbitrary(arbitrary[Int].map(NsRows.apply(_)))
  given gmosNsRowsCogen: Cogen[NsRows]       =
    Cogen[Int].contramap(_.value)
  given gmosNsCyclesArb: Arbitrary[NsCycles] =
    Arbitrary(arbitrary[Int].map(NsCycles.apply(_)))
  given gmosNsCyclesCogen: Cogen[NsCycles]   =
    Cogen[Int].contramap(_.value)
}

object ArbGmosParameters extends ArbGmosParameters
