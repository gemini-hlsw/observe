// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.enums.Instrument
import lucuma.core.math.SignalToNoise
import lucuma.core.math.arb.ArbSignalToNoise
import lucuma.core.model.sequence.Step
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.TelescopeConfig
import lucuma.core.model.sequence.arb.ArbStepConfig.given
import lucuma.core.model.sequence.arb.ArbTelescopeConfig.given
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.*
import observe.model.enums.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen

import ArbDhsTypes.given
import ArbInstrumentDynamicConfig.given
import ArbSignalToNoise.given
import ArbStepState.given
import ArbSystem.given

trait ArbStandardStep {

  given Arbitrary[ObserveStep.Standard] = Arbitrary[ObserveStep.Standard] {
    for {
      id <- arbitrary[Step.Id]
      d  <- arbitrary[InstrumentDynamicConfig]
      t  <- arbitrary[StepConfig]
      s  <- arbitrary[StepState]
      tc <- arbitrary[TelescopeConfig]
      sn <- arbitrary[Option[SignalToNoise]]
      f  <- arbitrary[Option[dhs.ImageFileId]]
      cs <- arbitrary[List[(Resource, ActionStatus)]]
      os <- arbitrary[ActionStatus]
    } yield ObserveStep.Standard(
      id = id,
      instConfig = d,
      stepConfig = t,
      telescopeConfig = tc,
      signalToNoise = sn,
      status = s,
      fileId = f,
      configStatus = cs,
      observeStatus = os
    )
  }

  given Cogen[ObserveStep.Standard] =
    Cogen[
      (
        Step.Id,
        InstrumentDynamicConfig,
        StepConfig,
        TelescopeConfig,
        Option[SignalToNoise],
        StepState,
        Option[dhs.ImageFileId],
        List[(Resource | Instrument, ActionStatus)],
        ActionStatus
      )
    ].contramap(s =>
      (s.id,
       s.instConfig,
       s.stepConfig,
       s.telescopeConfig,
       s.signalToNoise,
       s.status,
       s.fileId,
       s.configStatus,
       s.observeStatus
      )
    )

}

object ArbStandardStep extends ArbStandardStep
