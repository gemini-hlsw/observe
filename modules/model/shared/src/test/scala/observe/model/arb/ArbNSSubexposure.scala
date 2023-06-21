// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen
import lucuma.core.util.arb.ArbEnumerated.*
import observe.model.*
import observe.model.enums.NodAndShuffleStage.*
import observe.model.enums.*
import observe.model.GmosParameters.*
import shapeless.tag

trait ArbNSSubexposure {
  given nsSubexposureArb: Arbitrary[NSSubexposure] = Arbitrary[NSSubexposure] {
    for {
      t <- Gen.posNum[Int].map(NsCycles.apply(_))
      c <- Gen.choose(0, t.value).map(NsCycles.apply(_))
      i <- Gen.choose(0, NsSequence.length - 1)
    } yield NSSubexposure(t, c, i).getOrElse(NSSubexposure.Zero)
  }

  given nsSubexposureCogen: Cogen[NSSubexposure] =
    Cogen[
      (
        Int,
        Int,
        Int,
        NodAndShuffleStage
      )
    ].contramap(s => (s.totalCycles.value, s.cycle.value, s.stageIndex, s.stage))

}

object ArbNSSubexposure extends ArbNSSubexposure
