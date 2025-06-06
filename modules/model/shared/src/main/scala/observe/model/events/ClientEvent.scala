// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.events

import cats.*
import cats.data.NonEmptyList
import cats.derived.*
import eu.timepit.refined.cats.*
import io.circe.Decoder
import io.circe.Encoder
import io.circe.refined.*
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Step
import lucuma.core.util.Enumerated
import observe.model.ClientConfig
import observe.model.Conditions
import observe.model.ExecutionState
import observe.model.LogMessage
import observe.model.Notification
import observe.model.ObservationProgress
import observe.model.Operator
import observe.model.SequenceView
import observe.model.SequencesQueue
import observe.model.UserPrompt.ChecksOverride
import observe.model.enums.Resource
import observe.model.given
import observe.model.odb.ObsRecordedIds

sealed trait ClientEvent derives Eq, Encoder.AsObject, Decoder

object ClientEvent:
  sealed trait AllClientEvent    extends ClientEvent derives Eq
  sealed trait SingleClientEvent extends ClientEvent derives Eq

  enum SingleActionState(val tag: String) derives Enumerated:
    case Started   extends SingleActionState("started")
    case Completed extends SingleActionState("completed")
    case Failed    extends SingleActionState("failed")

  case object BaDum extends AllClientEvent derives Eq:
    given Encoder[BaDum.type] = Encoder[String].contramap(_ => "BaDum")
    given Decoder[BaDum.type] = Decoder[String].flatMap:
      case "BaDum" => Decoder.const(BaDum)
      case _       => Decoder.failedWithMessage("Not a heartbeat event")

  case class InitialEvent(clientConfig: ClientConfig) extends AllClientEvent derives Eq

  case class ObserveState(
    sequenceExecution:  Map[Observation.Id, ExecutionState],
    conditions:         Conditions,
    operator:           Option[Operator],
    currentRecordedIds: ObsRecordedIds
  ) extends AllClientEvent
      derives Eq

  object ObserveState:
    def fromSequenceViewQueue(
      view:        SequencesQueue[SequenceView],
      recordedIds: ObsRecordedIds
    ): ObserveState =
      ObserveState(view.sequencesState, view.conditions, view.operator, recordedIds)

  case class StepComplete(obsId: Observation.Id) extends AllClientEvent derives Eq

  case class SequencePaused(obsId: Observation.Id) extends AllClientEvent derives Eq

  case class BreakpointReached(obsId: Observation.Id) extends AllClientEvent derives Eq

  case class AcquisitionPromptReached(obsId: Observation.Id) extends AllClientEvent derives Eq

  case class SingleActionEvent(
    obsId:     Observation.Id,
    stepId:    Step.Id,
    subsystem: Resource | Instrument,
    event:     SingleActionState,
    error:     Option[String]
  ) extends AllClientEvent
      derives Eq

  case class ChecksOverrideEvent(prompt: ChecksOverride) extends SingleClientEvent derives Eq

  case class ProgressEvent(progress: ObservationProgress) extends AllClientEvent derives Eq

  case class AtomLoaded(obsId: Observation.Id, sequenceType: SequenceType, atomId: Atom.Id)
      extends AllClientEvent derives Eq

  case class UserNotification(memo: Notification) extends SingleClientEvent derives Eq

  case class LogEvent(msg: LogMessage) extends AllClientEvent derives Eq

  case class SequenceComplete(obsId: Observation.Id) extends AllClientEvent derives Eq

  case class SequenceFailed(obsId: Observation.Id, errorMsg: String) extends AllClientEvent
      derives Eq
