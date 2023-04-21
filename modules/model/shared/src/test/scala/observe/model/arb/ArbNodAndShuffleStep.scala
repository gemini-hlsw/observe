// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalacheck.Arbitrary.*
import lucuma.core.util.arb.ArbEnumerated.*
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*
import observe.model.*
import observe.model.GmosParameters.*
import observe.model.NodAndShuffleStep.{PauseGracefully, PendingObserveCmd, StopGracefully}
import observe.model.enums.*
import observe.model.arb.ArbStepConfig.{*, given}
import observe.model.arb.ArbStepState.{*, given}
import observe.model.arb.ArbDhsTypes.{*, given}
import observe.model.arb.ArbTime.{*, given}
import observe.model.arb.ArbGmosParameters.{*, given}
import observe.model.arb.ArbNSRunningState.{*, given}
import squants._

trait ArbNodAndShuffleStep {
  given nssArb: Arbitrary[NodAndShuffleStatus] = Arbitrary[NodAndShuffleStatus] {
    for {
      as <- arbitrary[ActionStatus]
      t  <- arbitrary[Time]
      n  <- arbitrary[Time]
      c  <- arbitrary[NsCycles]
      s  <- arbitrary[Option[NSRunningState]]
    } yield NodAndShuffleStatus(as, t, n, c, s)
  }

  given nodAndShuffleStatusCogen: Cogen[NodAndShuffleStatus] =
    Cogen[(ActionStatus, Time, Time, NsCycles)].contramap { x =>
      (x.observing, x.totalExposureTime, x.nodExposureTime, x.cycles)
    }

  given nodAndShufflePendingCmdArb: Arbitrary[PendingObserveCmd] =
    Arbitrary[PendingObserveCmd](
      Gen.oneOf(List(PauseGracefully, StopGracefully))
    )

  given nodShuffleStepArb: Arbitrary[NodAndShuffleStep] = Arbitrary[NodAndShuffleStep] {
    for {
      id <- arbitrary[StepId]
      c  <- stepConfigGen
      s  <- arbitrary[StepState]
      b  <- arbitrary[Boolean]
      k  <- arbitrary[Boolean]
      f  <- arbitrary[Option[dhs.ImageFileId]]
      cs <- arbitrary[List[(Resource, ActionStatus)]]
      os <- arbitrary[NodAndShuffleStatus]
      oc <- arbitrary[Option[PendingObserveCmd]]
    } yield new NodAndShuffleStep(id = id,
                                  config = c,
                                  status = s,
                                  breakpoint = b,
                                  skip = k,
                                  fileId = f,
                                  configStatus = cs,
                                  nsStatus = os,
                                  pendingObserveCmd = oc
    )
  }

  given nodShuffleStepCogen: Cogen[NodAndShuffleStep] =
    Cogen[
      (
        StepId,
        Map[SystemName, Map[String, String]],
        StepState,
        Boolean,
        Boolean,
        Option[dhs.ImageFileId],
        List[(Resource, ActionStatus)],
        NodAndShuffleStatus
      )
    ].contramap(s =>
      (s.id, s.config, s.status, s.breakpoint, s.skip, s.fileId, s.configStatus, s.nsStatus)
    )

}

object ArbNodAndShuffleStep extends ArbNodAndShuffleStep
