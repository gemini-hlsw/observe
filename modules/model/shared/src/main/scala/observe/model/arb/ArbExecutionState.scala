// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import observe.model.ExecutionState
import observe.model.arb.ObserveModelArbitraries.given
import observe.model.SequenceState
import observe.model.Observer
import lucuma.core.model.sequence.Step
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.NsRunningState
import observe.model.arb.ArbNsRunningState.given
import observe.model.enums.Resource
import lucuma.core.enums.Instrument
import observe.model.enums.ActionStatus
import observe.model.SystemOverrides

trait ArbExecutionState:
  given Arbitrary[ExecutionState] = Arbitrary:
    for
      sequenceState   <- arbitrary[SequenceState]
      observer        <- arbitrary[Option[Observer]]
      runningStepId   <- arbitrary[Option[Step.Id]]
      nsState         <- arbitrary[Option[NsRunningState]]
      stepResources   <- arbitrary[Map[Step.Id, Map[Resource | Instrument, ActionStatus]]]
      systemOverrides <- arbitrary[SystemOverrides]
      breakpoints     <- arbitrary[Set[Step.Id]]
    yield ExecutionState(
      sequenceState,
      observer,
      runningStepId,
      nsState,
      stepResources,
      systemOverrides,
      breakpoints
    )

object ArbExecutionState extends ArbExecutionState
