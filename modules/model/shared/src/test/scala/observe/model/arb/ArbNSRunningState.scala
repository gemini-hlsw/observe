// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import lucuma.core.util.arb.ArbEnumerated.*
import observe.model.*
import observe.model.enums.*
import observe.model.arb.ArbNSSubexposure.{*, given}

trait ArbNSRunningState {
  given nsRunningStateArb: Arbitrary[NSRunningState] = Arbitrary[NSRunningState] {
    for {
      a <- arbitrary[NSAction]
      u <- arbitrary[NSSubexposure]
    } yield NSRunningState(a, u)
  }

  given nsRunningStateCogen: Cogen[NSRunningState] =
    Cogen[(NSAction, NSSubexposure)].contramap { x =>
      (x.action, x.sub)
    }

}

object ArbNSRunningState extends ArbNSRunningState
