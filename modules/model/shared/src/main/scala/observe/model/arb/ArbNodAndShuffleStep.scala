// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.Step
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.arb.ArbStepConfig.given
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.arb.ArbDynamicConfig.given
import lucuma.core.util.TimeSpan
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbTimeSpan.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.*
import observe.model.GmosParameters.*
import observe.model.enums.ActionStatus
import observe.model.enums.PendingObserveCmd
import observe.model.enums.PendingObserveCmd.*
import observe.model.enums.Resource
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

import ArbDhsTypes.given
import ArbGmosParameters.given
import ArbNsRunningState.given
import ArbStepState.given
import ArbSystem.given

trait ArbNodAndShuffleStep {

  given Arbitrary[NodAndShuffleStatus] = Arbitrary[NodAndShuffleStatus] {
    for {
      as <- arbitrary[ActionStatus]
      t  <- arbitrary[TimeSpan]
      n  <- arbitrary[TimeSpan]
      c  <- arbitrary[NsCycles]
      s  <- arbitrary[Option[NsRunningState]]
    } yield NodAndShuffleStatus(as, t, n, c, s)
  }

  given Cogen[NodAndShuffleStatus] =
    Cogen[(ActionStatus, TimeSpan, TimeSpan, NsCycles, Option[NsRunningState])].contramap { x =>
      (x.observing, x.totalExposureTime, x.nodExposureTime, x.cycles, x.state)
    }

  given Arbitrary[PendingObserveCmd] =
    Arbitrary[PendingObserveCmd](
      Gen.oneOf(List(PauseGracefully, StopGracefully))
    )

  given nodShuffleStepArb: Arbitrary[ObserveStep.NodAndShuffle] =
    Arbitrary[ObserveStep.NodAndShuffle] {
      for {
        id <- arbitrary[Step.Id]
        d  <- arbitrary[DynamicConfig]
        t  <- arbitrary[StepConfig]
        s  <- arbitrary[StepState]
        b  <- arbitrary[Breakpoint]
        k  <- arbitrary[Boolean]
        f  <- arbitrary[Option[dhs.ImageFileId]]
        cs <- arbitrary[List[(Resource, ActionStatus)]]
        os <- arbitrary[NodAndShuffleStatus]
        oc <- arbitrary[Option[PendingObserveCmd]]
      } yield ObserveStep.NodAndShuffle(
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

  given nodShuffleStepCogen: Cogen[ObserveStep.NodAndShuffle] =
    Cogen[
      (
        Step.Id,
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
