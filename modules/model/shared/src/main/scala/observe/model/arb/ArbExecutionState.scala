// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.Step
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.ExecutionState
import observe.model.NsRunningState
import observe.model.ObserveStep
import observe.model.Observer
import observe.model.SequenceState
import observe.model.SystemOverrides
import observe.model.arb.ArbNsRunningState.given
import observe.model.arb.ObserveModelArbitraries.given
import observe.model.enums.ActionStatus
import observe.model.enums.Resource
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Cogen

import ArbObserveStep.given

trait ArbExecutionState:
  given Arbitrary[ExecutionState] = Arbitrary:
    for
      sequenceState   <- arbitrary[SequenceState]
      observer        <- arbitrary[Option[Observer]]
      steps           <- arbitrary[List[ObserveStep]]
      runningStepId   <- arbitrary[Option[Step.Id]]
      nsState         <- arbitrary[Option[NsRunningState]]
      stepResources   <- arbitrary[Map[Step.Id, Map[Resource | Instrument, ActionStatus]]]
      systemOverrides <- arbitrary[SystemOverrides]
      breakpoints     <- arbitrary[Set[Step.Id]]
    yield ExecutionState(
      sequenceState,
      observer,
      steps,
      runningStepId,
      nsState,
      stepResources,
      systemOverrides,
      breakpoints
    )

  given Cogen[ExecutionState] =
    Cogen[
      (
        SequenceState,
        Option[Observer],
        Option[Step.Id],
        Option[NsRunningState],
        List[(Step.Id, List[(Resource | Instrument, ActionStatus)])],
        SystemOverrides,
        List[Step.Id]
      )
    ].contramap: x =>
      (x.sequenceState,
       x.observer,
       x.runningStepId,
       x.nsState,
       x.stepResources.view.mapValues(_.toList).toList,
       x.systemOverrides,
       x.breakpoints.toList
      )

object ArbExecutionState extends ArbExecutionState
