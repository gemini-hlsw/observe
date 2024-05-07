// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.events

import cats.*
import cats.data.NonEmptyList
import cats.derived.*
import cats.syntax.all.*
import eu.timepit.refined.cats.*
import io.circe.Decoder
import io.circe.Encoder
import io.circe.refined.*
import io.circe.syntax.*
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Step
import lucuma.core.util.Enumerated
import observe.model.ClientConfig
import observe.model.Conditions
import observe.model.ExecutionState
import observe.model.ObservationProgress
import observe.model.Operator
import observe.model.SequenceView
import observe.model.SequencesQueue
import observe.model.UserPrompt.ChecksOverride
import observe.model.enums.Resource
import observe.model.given
import observe.model.odb.ObsRecordedIds

sealed trait ClientEvent derives Eq

object ClientEvent:
  sealed trait AllClientEvent    extends ClientEvent derives Eq
  sealed trait SingleClientEvent extends ClientEvent derives Eq

  enum SingleActionState(val tag: String) derives Enumerated:
    case Started   extends SingleActionState("started")
    case Completed extends SingleActionState("completed")
    case Failed    extends SingleActionState("failed")

  case object BaDum extends AllClientEvent: // derives Eq, Encoder, Decoder
    given Encoder[BaDum.type] = Encoder[String].contramap(_ => "BaDum")
    given Decoder[BaDum.type] = Decoder[String].flatMap:
      case "BaDum" => Decoder.const(BaDum)
      case _       => Decoder.failedWithMessage("Not a heartbeat event")

  case class InitialEvent(clientConfig: ClientConfig) extends AllClientEvent
      derives Eq,
        Encoder.AsObject,
        Decoder

  case class ObserveState(
    sequenceExecution:  Map[Observation.Id, ExecutionState],
    conditions:         Conditions,
    operator:           Option[Operator],
    currentRecordedIds: ObsRecordedIds
  ) extends AllClientEvent
      derives Eq,
        Encoder.AsObject,
        Decoder

  object ObserveState:
    def fromSequenceViewQueue(
      view:        SequencesQueue[SequenceView],
      recordedIds: ObsRecordedIds
    ): ObserveState =
      ObserveState(view.sequencesState, view.conditions, view.operator, recordedIds)

  case class SingleActionEvent(
    obsId:     Observation.Id,
    stepId:    Step.Id,
    subsystem: Resource | Instrument,
    event:     SingleActionState,
    error:     Option[String]
  ) extends AllClientEvent
      derives Eq,
        Encoder.AsObject,
        Decoder

  case class ChecksOverrideEvent(prompt: ChecksOverride) extends SingleClientEvent
      derives Eq,
        Encoder.AsObject,
        Decoder

  case class ProgressEvent(progress: ObservationProgress) extends AllClientEvent
      derives Eq,
        Encoder.AsObject,
        Decoder

  case class AtomLoaded(obsId: Observation.Id, sequenceType: SequenceType, atomId: Atom.Id)
      extends AllClientEvent derives Eq, Encoder.AsObject, Decoder

  given Encoder[ClientEvent] = Encoder.instance:
    case e @ BaDum                            => e.asJson
    case e @ InitialEvent(_)                  => e.asJson
    case e @ ObserveState(_, _, _, _)         => e.asJson
    case e @ SingleActionEvent(_, _, _, _, _) => e.asJson
    case e @ ChecksOverrideEvent(_)           => e.asJson
    case e @ ProgressEvent(_)                 => e.asJson
    case e @ AtomLoaded(_, _, _)              => e.asJson

  given Decoder[ClientEvent] =
    List[Decoder[ClientEvent]](
      Decoder[BaDum.type].widen,
      Decoder[InitialEvent].widen,
      Decoder[ObserveState].widen,
      Decoder[SingleActionEvent].widen,
      Decoder[ChecksOverrideEvent].widen,
      Decoder[ProgressEvent].widen,
      Decoder[AtomLoaded].widen
    ).reduceLeft(_ or _)
