// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.util.arb.ArbEnumerated.*
import observe.model.GmosParameters.*
import observe.model.*
import observe.model.enums.NodAndShuffleStage.*
import observe.model.enums.*
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait ArbNsSubexposure {
  given NsSubexposureArb: Arbitrary[NsSubexposure] = Arbitrary[NsSubexposure] {
    for {
      t <- Gen.posNum[Int].map(NsCycles.apply(_))
      c <- Gen.choose(0, t.value).map(NsCycles.apply(_))
      i <- Gen.choose(0, NsSequence.length - 1)
    } yield NsSubexposure(t, c, i).getOrElse(NsSubexposure.Zero)
  }

  given NsSubexposureCogen: Cogen[NsSubexposure] =
    Cogen[
      (
        Int,
        Int,
        Int,
        NodAndShuffleStage
      )
    ].contramap(s => (s.totalCycles.value, s.cycle.value, s.stageIndex, s.stage))

}

object ArbNsSubexposure extends ArbNsSubexposure
