// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Step
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbGid.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.ClientConfig
import observe.model.Conditions
import observe.model.ExecutionState
import observe.model.LogMessage
import observe.model.Notification
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
import observe.model.events.*
import observe.model.events.ClientEvent.SingleActionState
import observe.model.odb.ObsRecordedIds
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

import ArbNotification.given
import ArbNsRunningState.given
import ArbObservationProgress.given
import ArbSystem.given
import ArbUserPrompt.given
import ArbClientConfig.given
import ArbObsRecordedIds.given
import ArbLogMessage.given

trait ArbClientEvent:

  given Arbitrary[ClientEvent.ObserveState] = Arbitrary:
    for
      s    <- arbitrary[SequencesQueue[SequenceView]]
      c    <- arbitrary[Conditions]
      o    <- arbitrary[Option[Operator]]
      rids <- arbitrary[ObsRecordedIds]
    yield ClientEvent.ObserveState(s.sequencesState, c, o, rids)

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
    Cogen[(List[(Observation.Id, ExecutionState)], Conditions, ObsRecordedIds)].contramap(x =>
      (x.sequenceExecution.toList, x.conditions, x.currentRecordedIds)
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

  given Arbitrary[ClientEvent.AtomLoaded] = Arbitrary:
    for
      obsId        <- arbitrary[Observation.Id]
      sequenceType <- arbitrary[SequenceType]
      atomId       <- arbitrary[Atom.Id]
    yield ClientEvent.AtomLoaded(obsId, sequenceType, atomId)

  given Cogen[ClientEvent.AtomLoaded] =
    Cogen[(Observation.Id, SequenceType, Atom.Id)].contramap: x =>
      (x.obsId, x.sequenceType, x.atomId)

  given Arbitrary[ClientEvent.UserNotification] = Arbitrary:
    arbitrary[Notification].map(ClientEvent.UserNotification(_))

  given Cogen[ClientEvent.UserNotification] =
    Cogen[Notification].contramap(_.memo)

  given Arbitrary[ClientEvent.LogEvent] = Arbitrary:
    arbitrary[LogMessage].map(ClientEvent.LogEvent(_))

  given Cogen[ClientEvent.LogEvent] =
    Cogen[LogMessage].contramap(_.msg)

  given Arbitrary[ClientEvent] = Arbitrary:
    Gen.oneOf(
      arbitrary[ClientEvent.InitialEvent],
      arbitrary[ClientEvent.ObserveState],
      arbitrary[ClientEvent.SingleActionEvent],
      arbitrary[ClientEvent.ChecksOverrideEvent],
      arbitrary[ClientEvent.ProgressEvent],
      arbitrary[ClientEvent.AtomLoaded],
      arbitrary[ClientEvent.UserNotification],
      arbitrary[ClientEvent.LogEvent]
    )

  given Cogen[ClientEvent] =
    Cogen[Either[
      Unit,
      Either[
        ClientEvent.InitialEvent,
        Either[
          ClientEvent.ObserveState,
          Either[
            ClientEvent.SingleActionEvent,
            Either[
              ClientEvent.ChecksOverrideEvent,
              Either[
                ClientEvent.ProgressEvent,
                Either[
                  ClientEvent.AtomLoaded,
                  Either[
                    ClientEvent.UserNotification,
                    ClientEvent.LogEvent
                  ]
                ]
              ]
            ]
          ]
        ]
      ]
    ]].contramap:
      case ClientEvent.BaDum                                => Left(())
      case e @ ClientEvent.InitialEvent(_)                  => Right(Left(e))
      case e @ ClientEvent.ObserveState(_, _, _, _)         => Right(Right(Left(e)))
      case e @ ClientEvent.SingleActionEvent(_, _, _, _, _) => Right(Right(Right(Left(e))))
      case e @ ClientEvent.ChecksOverrideEvent(_)           => Right(Right(Right(Right(Left(e)))))
      case e @ ClientEvent.ProgressEvent(_)                 => Right(Right(Right(Right(Right(Left(e))))))
      case e @ ClientEvent.AtomLoaded(_, _, _)              => Right(Right(Right(Right(Right(Right(Left(e)))))))
      case e @ ClientEvent.UserNotification(_)              =>
        Right(Right(Right(Right(Right(Right(Right(Left(e))))))))
      case e @ ClientEvent.LogEvent(_)                      => Right(Right(Right(Right(Right(Right(Right(Right(e))))))))

end ArbClientEvent

object ArbClientEvent extends ArbClientEvent
