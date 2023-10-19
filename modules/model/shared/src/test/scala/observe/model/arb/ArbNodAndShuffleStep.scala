// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.arb.ArbStepConfig.*
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.arb.ArbDynamicConfig.*
import lucuma.core.util.TimeSpan
import lucuma.core.util.arb.ArbEnumerated.*
import lucuma.core.util.arb.ArbTimeSpan.given
import lucuma.core.util.arb.ArbUid.*
import observe.model.GmosParameters.*
import observe.model.NodAndShuffleStep.PauseGracefully
import observe.model.NodAndShuffleStep.PendingObserveCmd
import observe.model.NodAndShuffleStep.StopGracefully
import observe.model.*
import observe.model.arb.ArbDhsTypes.given
import observe.model.arb.ArbGmosParameters.given
import observe.model.arb.ArbNsRunningState.given
import observe.model.arb.ArbStepState.given
import observe.model.arb.ArbSystem.given
import observe.model.enums.ActionStatus
import observe.model.enums.Resource
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait ArbNodAndShuffleStep {

  given nssArb: Arbitrary[NodAndShuffleStatus] = Arbitrary[NodAndShuffleStatus] {
    for {
      as <- arbitrary[ActionStatus]
      t  <- arbitrary[TimeSpan]
      n  <- arbitrary[TimeSpan]
      c  <- arbitrary[NsCycles]
      s  <- arbitrary[Option[NsRunningState]]
    } yield NodAndShuffleStatus(as, t, n, c, s)
  }

  given nodAndShuffleStatusCogen: Cogen[NodAndShuffleStatus] =
    Cogen[(ActionStatus, TimeSpan, TimeSpan, NsCycles, Option[NsRunningState])].contramap { x =>
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
      b  <- arbitrary[Breakpoint]
      k  <- arbitrary[Boolean]
      f  <- arbitrary[Option[dhs.ImageFileId]]
      cs <- arbitrary[List[(Resource, ActionStatus)]]
      os <- arbitrary[NodAndShuffleStatus]
      oc <- arbitrary[Option[PendingObserveCmd]]
    } yield new NodAndShuffleStep(
      id = id,
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
        Breakpoint,
        Boolean,
        Option[dhs.ImageFileId],
        List[(Resource | Instrument, ActionStatus)],
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
