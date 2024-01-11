// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbGid.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.Conditions
import observe.model.ClientConfig
import observe.model.ExecutionState
import observe.model.NsRunningState
import observe.model.ObservationProgress
import observe.model.Operator
import observe.model.SequenceState
import observe.model.SequenceView
import observe.model.SequencesQueue
import observe.model.UserPrompt
import observe.model.UserPrompt.ChecksOverride
import observe.model.arb.ObserveModelArbitraries.given
import observe.model.enums.ActionStatus
import observe.model.enums.Resource
import observe.model.events.client.ClientEvent.SingleActionState
import observe.model.events.client.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

import ArbNsRunningState.given
import ArbObservationProgress.given
import ArbSystem.given
import ArbUserPrompt.given
import ArbClientConfig.given

trait ArbClientEvent:

  given Arbitrary[ClientEvent.ObserveState] = Arbitrary:
    for
      s <- arbitrary[SequencesQueue[SequenceView]]
      c <- arbitrary[Conditions]
      o <- arbitrary[Option[Operator]]
    yield ClientEvent.ObserveState(s.sequencesState, c, o)

  given Cogen[ExecutionState] =
    Cogen[
      (SequenceState,
       Option[Step.Id],
       Option[NsRunningState],
       List[(Step.Id, List[(Resource | Instrument, ActionStatus)])],
       List[Step.Id]
      )
    ].contramap(x =>
      (x.sequenceState,
       x.runningStepId,
       x.nsState,
       x.stepResources.view.mapValues(_.toList).toList,
       x.breakpoints.toList
      )
    )

  given Cogen[ClientEvent.ObserveState] =
    Cogen[(List[(Observation.Id, ExecutionState)], Conditions)].contramap(x =>
      (x.sequenceExecution.toList, x.conditions)
    )

  given Arbitrary[ClientEvent.InitialEvent] = Arbitrary:
    arbitrary[ClientConfig].map(ClientEvent.InitialEvent(_))

  given Cogen[ClientEvent.InitialEvent] =
    Cogen[ClientConfig].contramap(_.clientConfig)

  given Arbitrary[ClientEvent.SingleActionEvent] = Arbitrary:
    for
      o  <- arbitrary[Observation.Id]
      s  <- arbitrary[Step.Id]
      ss <- arbitrary[Resource | Instrument]
      t  <- arbitrary[SingleActionState]
      e  <- arbitrary[Option[String]]
    yield ClientEvent.SingleActionEvent(o, s, ss, t, e)

  given Cogen[ClientEvent.SingleActionEvent] =
    Cogen[(Observation.Id, Step.Id, Resource | Instrument, SingleActionState, Option[String])]
      .contramap(x => (x.obsId, x.stepId, x.subsystem, x.event, x.error))

  given Arbitrary[ClientEvent.ChecksOverrideEvent] = Arbitrary:
    arbitrary[ChecksOverride].map(u => ClientEvent.ChecksOverrideEvent(u))

  given Cogen[ClientEvent.ChecksOverrideEvent] =
    Cogen[ChecksOverride].contramap(_.prompt)

  given Arbitrary[ClientEvent.ProgressEvent] = Arbitrary:
    arbitrary[ObservationProgress].map(p => ClientEvent.ProgressEvent(p))

  given Cogen[ClientEvent.ProgressEvent] =
    Cogen[ObservationProgress].contramap(_.progress)

  given Arbitrary[ClientEvent] = Arbitrary:
    Gen.oneOf(
      arbitrary[ClientEvent.InitialEvent],
      arbitrary[ClientEvent.ObserveState],
      arbitrary[ClientEvent.SingleActionEvent],
      arbitrary[ClientEvent.ChecksOverrideEvent],
      arbitrary[ClientEvent.ProgressEvent]
    )

  given Cogen[ClientEvent] =
    Cogen[Either[
      ClientEvent.InitialEvent,
      Either[ClientEvent.ObserveState, Either[
        ClientEvent.SingleActionEvent,
        Either[ClientEvent.ChecksOverrideEvent, ClientEvent.ProgressEvent]
      ]]
    ]].contramap:
      case e: ClientEvent.InitialEvent        => Left(e)
      case e: ClientEvent.ObserveState        => Right(Left(e))
      case e: ClientEvent.SingleActionEvent   => Right(Right(Left(e)))
      case e: ClientEvent.ChecksOverrideEvent => Right(Right(Right(Left(e))))
      case e: ClientEvent.ProgressEvent       => Right(Right(Right(Right(e))))

end ArbClientEvent

object ArbClientEvent extends ArbClientEvent
