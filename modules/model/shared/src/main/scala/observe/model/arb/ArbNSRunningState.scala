// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.util.arb.ArbEnumerated.given
import observe.model.*
import observe.model.enums.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen

import ArbNsSubexposure.given

trait ArbNsRunningState {
  given NsRunningStateArb: Arbitrary[NsRunningState] = Arbitrary[NsRunningState] {
    for {
      a <- arbitrary[NsAction]
      u <- arbitrary[NsSubexposure]
    } yield NsRunningState(a, u)
  }

  given NsRunningStateCogen: Cogen[NsRunningState] =
    Cogen[(NsAction, NsSubexposure)].contramap { x =>
      (x.action, x.sub)
    }

}

object ArbNsRunningState extends ArbNsRunningState
