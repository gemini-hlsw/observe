// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.common.arb

import observe.common.FixedLengthBuffer
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait ArbFixedLengthBuffer:
  given [A: Arbitrary]: Arbitrary[FixedLengthBuffer[A]] =
    Arbitrary:
      val maxSize = 100
      for
        l <- Gen.choose(1, maxSize)
        s <- Gen.choose(0, l - 1)
        d <- Gen.listOfN(s, arbitrary[A])
      yield FixedLengthBuffer.unsafe(l, d*)

  given [A: Cogen]: Cogen[FixedLengthBuffer[A]] =
    Cogen[(Int, Vector[A])].contramap(x => (x.maxLength, x.toChain.toVector))

object ArbFixedLengthBuffer extends ArbFixedLengthBuffer
