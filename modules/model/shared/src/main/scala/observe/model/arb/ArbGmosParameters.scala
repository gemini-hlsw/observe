// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.arb.newTypeArbitrary
import lucuma.core.arb.newTypeCogen
import observe.model.GmosParameters.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen

trait ArbGmosParameters {
  given Arbitrary[NsPairs] = newTypeArbitrary(NsPairs)
  given Cogen[NsPairs]     = newTypeCogen(NsPairs)

  given Arbitrary[NsStageIndex] = newTypeArbitrary(NsStageIndex)
  given Cogen[NsStageIndex]     = newTypeCogen(NsStageIndex)

  given Arbitrary[NsRows] = newTypeArbitrary(NsRows)
  given Cogen[NsRows]     = newTypeCogen(NsRows)

  given Arbitrary[NsCycles] = newTypeArbitrary(NsCycles)
  given Cogen[NsCycles]     = newTypeCogen(NsCycles)
}

object ArbGmosParameters extends ArbGmosParameters
