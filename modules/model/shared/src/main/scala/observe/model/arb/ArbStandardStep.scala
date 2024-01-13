// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.arb.ArbStepConfig.*
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.arb.ArbDynamicConfig.*
import lucuma.core.util.arb.ArbEnumerated.*
import lucuma.core.util.arb.ArbUid.*
import observe.model.*
import observe.model.enums.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen

import ArbDhsTypes.given
import ArbStepState.given
import ArbSystem.given

trait ArbStandardStep {

  given stsArb: Arbitrary[Step.Standard] = Arbitrary[Step.Standard] {
    for {
      id <- arbitrary[StepId]
      d  <- arbitrary[DynamicConfig]
      t  <- arbitrary[StepConfig]
      s  <- arbitrary[StepState]
      b  <- arbitrary[Breakpoint]
      k  <- arbitrary[Boolean]
      f  <- arbitrary[Option[dhs.ImageFileId]]
      cs <- arbitrary[List[(Resource, ActionStatus)]]
      os <- arbitrary[ActionStatus]
    } yield Step.Standard(
      id = id,
      instConfig = d,
      stepConfig = t,
      status = s,
      breakpoint = b,
      skip = k,
      fileId = f,
      configStatus = cs,
      observeStatus = os
    )
  }

  given standardStepCogen: Cogen[Step.Standard] =
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
        ActionStatus
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
       s.observeStatus
      )
    )

}

object ArbStandardStep extends ArbStandardStep
