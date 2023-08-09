// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.arb.ArbDynamicConfig.*
import lucuma.core.model.sequence.arb.ArbStepConfig.*
import lucuma.core.model.sequence.StepConfig
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalacheck.Arbitrary.*
import lucuma.core.util.arb.ArbEnumerated.*
import lucuma.core.util.arb.ArbUid.*
import lucuma.core.util.TimeSpan
import lucuma.core.util.arb.ArbTimeSpan.given
import observe.model.*
import observe.model.GmosParameters.*
import observe.model.NodAndShuffleStep.{PauseGracefully, PendingObserveCmd, StopGracefully}
import observe.model.enums.*
import observe.model.arb.ArbStepState.given
import observe.model.arb.ArbDhsTypes.given
import observe.model.arb.ArbGmosParameters.given
import observe.model.arb.ArbNSRunningState.given

trait ArbNodAndShuffleStep {

  given nssArb: Arbitrary[NodAndShuffleStatus] = Arbitrary[NodAndShuffleStatus] {
    for {
      as <- arbitrary[ActionStatus]
      t  <- arbitrary[TimeSpan]
      n  <- arbitrary[TimeSpan]
      c  <- arbitrary[NsCycles]
      s  <- arbitrary[Option[NSRunningState]]
    } yield NodAndShuffleStatus(as, t, n, c, s)
  }

  given nodAndShuffleStatusCogen: Cogen[NodAndShuffleStatus] =
    Cogen[(ActionStatus, TimeSpan, TimeSpan, NsCycles, Option[NSRunningState])].contramap { x =>
      (x.observing, x.totalExposureTime, x.nodExposureTime, x.cycles, x.state)
    }

  given nodAndShufflePendingCmdArb: Arbitrary[PendingObserveCmd] =
    Arbitrary[PendingObserveCmd](
      Gen.oneOf(List(PauseGracefully, StopGracefully))
    )

  given nodShuffleStepArb: Arbitrary[NodAndShuffleStep] = Arbitrary[NodAndShuffleStep] {
    for {
      id <- arbitrary[StepId]
      d  <- arbitrary[DynamicConfig]
      t  <- arbitrary[StepConfig]
      s  <- arbitrary[StepState]
      b  <- arbitrary[Boolean]
      k  <- arbitrary[Boolean]
      f  <- arbitrary[Option[dhs.ImageFileId]]
      cs <- arbitrary[List[(Resource, ActionStatus)]]
      os <- arbitrary[NodAndShuffleStatus]
      oc <- arbitrary[Option[PendingObserveCmd]]
    } yield new NodAndShuffleStep(id = id,
                                  instConfig = d,
                                  stepConfig = t,
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
        DynamicConfig,
        StepConfig,
        StepState,
        Boolean,
        Boolean,
        Option[dhs.ImageFileId],
        List[(Resource, ActionStatus)],
        NodAndShuffleStatus
      )
    ].contramap(s =>
      (s.id,
       s.instConfig,
       s.stepConfig,
       s.status,
       s.breakpoint,
       s.skip,
       s.fileId,
       s.configStatus,
       s.nsStatus
      )
    )

}

object ArbNodAndShuffleStep extends ArbNodAndShuffleStep
