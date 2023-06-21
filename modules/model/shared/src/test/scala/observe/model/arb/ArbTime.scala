// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import lucuma.core.arb.ArbTime.arbSDuration
import squants.time.*

trait ArbTime {

  given arbTime: Arbitrary[Time] =
    Arbitrary {
      arbSDuration.arbitrary.map(Time.apply)
    }

  given timeCogen: Cogen[Time] =
    Cogen[Long]
      .contramap(_.millis)

}

object ArbTime extends ArbTime
