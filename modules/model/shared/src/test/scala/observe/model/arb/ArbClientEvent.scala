// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbGid.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.Conditions
import observe.model.Environment
import observe.model.ExecutionState
import observe.model.NsRunningState
import observe.model.ObserveModelArbitraries.given
import observe.model.SequenceState
import observe.model.SequenceView
import observe.model.SequencesQueue
import observe.model.arb.ArbNsRunningState.given
import observe.model.enums.ActionStatus
import observe.model.enums.Resource
import observe.model.events.client.ClientEvent.SingleActionState
import observe.model.events.client.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

import ArbEnvironment.given

trait ArbClientEvent:

  given Arbitrary[ClientEvent.ObserveState] = Arbitrary:
    for
      s <- arbitrary[SequencesQueue[SequenceView]]
      c <- arbitrary[Conditions]
    yield ClientEvent.ObserveState(s.sequencesState, c)

  given Cogen[ExecutionState] =
    Cogen[
      (SequenceState,
       Option[Step.Id],
       Option[NsRunningState],
       List[(Resource, ActionStatus)],
       List[Step.Id]
      )
    ].contramap(x =>
      (x.sequenceState, x.runningStepId, x.nsState, x.configStatus, x.breakpoints.toList)
    )

  given Cogen[ClientEvent.ObserveState] =
    Cogen[(List[(Observation.Id, ExecutionState)], Conditions)].contramap(x =>
      (x.sequenceExecution.toList, x.conditions)
    )

  given Arbitrary[ClientEvent.InitialEvent] = Arbitrary:
    arbitrary[Environment].map(ClientEvent.InitialEvent(_))

  given Cogen[ClientEvent.InitialEvent] =
    Cogen[Environment].contramap(_.environment)

  given Arbitrary[ClientEvent.SingleActionEvent] = Arbitrary:
    for
      o <- arbitrary[Observation.Id]
      s <- arbitrary[Step.Id]
      r <- arbitrary[Resource]
      t <- arbitrary[SingleActionState]
      e <- arbitrary[Option[String]]
    yield ClientEvent.SingleActionEvent(o, s, r, t, e)

  given Cogen[ClientEvent.SingleActionEvent] =
    Cogen[(Observation.Id, Step.Id, Resource, SingleActionState, Option[String])].contramap(x =>
      (x.obsId, x.stepId, x.resource, x.event, x.error)
    )

  given Arbitrary[ClientEvent] = Arbitrary:
    for
      initial <- arbitrary[ClientEvent.InitialEvent]
      state   <- arbitrary[ClientEvent.ObserveState]
      step    <- arbitrary[ClientEvent.SingleActionEvent]
      r       <- Gen.oneOf(initial, state, step)
    yield r

  given Cogen[ClientEvent] =
    Cogen[Either[ClientEvent.InitialEvent, Either[ClientEvent.ObserveState,
                                                  ClientEvent.SingleActionEvent
    ]]].contramap:
      case e: ClientEvent.InitialEvent      => Left(e)
      case e: ClientEvent.ObserveState      => Right(Left(e))
      case e: ClientEvent.SingleActionEvent => Right(Right(e))

end ArbClientEvent

object ArbClientEvent extends ArbClientEvent
