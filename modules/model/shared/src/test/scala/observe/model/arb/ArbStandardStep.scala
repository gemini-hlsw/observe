// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import lucuma.core.util.arb.ArbEnumerated.*
import lucuma.core.util.arb.ArbUid.*
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.arb.ArbStepConfig.*
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.arb.ArbDynamicConfig.*
import observe.model.*
import observe.model.enums.*
import observe.model.arb.ArbStepState.given
import observe.model.arb.ArbDhsTypes.given

trait ArbStandardStep {

  given stsArb: Arbitrary[StandardStep] = Arbitrary[StandardStep] {
    for {
      id <- arbitrary[StepId]
      d  <- arbitrary[DynamicConfig]
      t  <- arbitrary[StepConfig]
      s  <- arbitrary[StepState]
      b  <- arbitrary[Boolean]
      k  <- arbitrary[Boolean]
      f  <- arbitrary[Option[dhs.ImageFileId]]
      cs <- arbitrary[List[(Resource, ActionStatus)]]
      os <- arbitrary[ActionStatus]
    } yield new StandardStep(id = id,
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

  given standardStepCogen: Cogen[StandardStep] =
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
