// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.events

import cats.*
import cats.derived.*
import cats.syntax.all.*
import observe.model.dhs.ImageFileId
import observe.model.enums.*
import observe.model.*
import observe.model.given
import org.typelevel.cats.time.given

import java.time.Instant
import observe.model.events.client.*

sealed trait ObserveEvent       extends Product with Serializable
sealed trait ObserveModelUpdate extends ObserveEvent {
  def view: SequencesQueue[SequenceView]
}

/**
 * Events implementing ForClient will be delivered only to the given clientId
 */
sealed trait ForClient extends ObserveEvent {
  def clientId: ClientId
}

case class ObservationProgressEvent(progress: Progress) extends ObserveEvent derives Eq

case class ServerLogMessage(level: ServerLogLevel, timestamp: Instant, msg: String)
    extends ObserveEvent derives Order

case object NullEvent extends ObserveEvent

case class ConnectionOpenEvent(
  userDetails:   Option[UserDetails],
  clientId:      ClientId,
  serverVersion: String
) extends ObserveEvent
    derives Eq

object ObserveModelUpdate {
  given Eq[ObserveModelUpdate] =
    Eq.instance {
      case (a: SequenceStart, b: SequenceStart)                             => a === b
      case (a: StepExecuted, b: StepExecuted)                               => a === b
      case (a: FileIdStepExecuted, b: FileIdStepExecuted)                   => a === b
      case (a: SequenceCompleted, b: SequenceCompleted)                     => a === b
      case (a: SequenceLoaded, b: SequenceLoaded)                           => a === b
      case (a: SequenceUnloaded, b: SequenceUnloaded)                       => a === b
      case (a: StepBreakpointChanged, b: StepBreakpointChanged)             => a === b
      case (a: OperatorUpdated, b: OperatorUpdated)                         => a === b
      case (a: ObserverUpdated, b: ObserverUpdated)                         => a === b
      case (a: ConditionsUpdated, b: ConditionsUpdated)                     => a === b
      case (a: StepSkipMarkChanged, b: StepSkipMarkChanged)                 => a === b
      case (a: SequencePauseRequested, b: SequencePauseRequested)           => a === b
      case (a: SequencePauseCanceled, b: SequencePauseCanceled)             => a === b
      case (a: SequenceRefreshed, b: SequenceRefreshed)                     => a === b
      case (a: ActionStopRequested, b: ActionStopRequested)                 => a === b
      case (a: SequenceUpdated, b: SequenceUpdated)                         => a === b
      case (a: SequencePaused, b: SequencePaused)                           => a === b
      case (a: ExposurePaused, b: ExposurePaused)                           => a === b
      case (a: SequenceError, b: SequenceError)                             => a === b
      case (a: LoadSequenceUpdated, b: LoadSequenceUpdated)                 => a === b
      case (a: ClearLoadedSequencesUpdated, b: ClearLoadedSequencesUpdated) => a === b
      case (a: QueueUpdated, b: QueueUpdated)                               => a === b
      case (a: SequenceStopped, b: SequenceStopped)                         => a === b
      case (a: SequenceAborted, b: SequenceAborted)                         => a === b
      case _                                                                => false
    }

  def unapply(u: ObserveModelUpdate): Option[SequencesQueue[SequenceView]] =
    Some(u.view)
}

case class SequenceStart(
  obsId:  Observation.Id,
  stepId: StepId,
  view:   SequencesQueue[SequenceView]
) extends ObserveModelUpdate
    derives Eq

case class StepExecuted(obsId: Observation.Id, view: SequencesQueue[SequenceView])
    extends ObserveModelUpdate derives Eq

case class FileIdStepExecuted(fileId: ImageFileId, view: SequencesQueue[SequenceView])
    extends ObserveModelUpdate derives Eq

case class SequenceCompleted(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate
    derives Eq

case class SequenceLoaded(obsId: Observation.Id, view: SequencesQueue[SequenceView])
    extends ObserveModelUpdate derives Eq

case class SequenceUnloaded(obsId: Observation.Id, view: SequencesQueue[SequenceView])
    extends ObserveModelUpdate derives Eq

case class StepBreakpointChanged(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate
    derives Eq

case class OperatorUpdated(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate derives Eq

case class QueueUpdated(op: QueueManipulationOp, view: SequencesQueue[SequenceView])
    extends ObserveModelUpdate derives Eq

case class SingleActionEvent(op: SingleActionOp) extends ObserveEvent derives Eq

case class LoadSequenceUpdated(
  i:        Instrument,
  obsId:    Observation.Id,
  view:     SequencesQueue[SequenceView],
  clientId: ClientId
) extends ObserveModelUpdate
    derives Eq

case class ClearLoadedSequencesUpdated(view: SequencesQueue[SequenceView])
    extends ObserveModelUpdate derives Eq

case class ObserverUpdated(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate derives Eq

case class OverridesUpdated(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate
    derives Eq

case class ConditionsUpdated(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate
    derives Eq

case class StepSkipMarkChanged(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate
    derives Eq

case class SequencePauseRequested(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate
    derives Eq

case class SequencePauseCanceled(obsId: Observation.Id, view: SequencesQueue[SequenceView])
    extends ObserveModelUpdate derives Eq

case class SequenceRefreshed(view: SequencesQueue[SequenceView], clientId: ClientId)
    extends ObserveModelUpdate
    with ForClient derives Eq

case class ActionStopRequested(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate
    derives Eq

case class SequenceStopped(obsId: Observation.Id, view: SequencesQueue[SequenceView])
    extends ObserveModelUpdate derives Eq

case class SequenceAborted(obsId: Observation.Id, view: SequencesQueue[SequenceView])
    extends ObserveModelUpdate derives Eq

case class SequenceUpdated(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate derives Eq

case class SequencePaused(obsId: Observation.Id, view: SequencesQueue[SequenceView])
    extends ObserveModelUpdate derives Eq

case class ExposurePaused(obsId: Observation.Id, view: SequencesQueue[SequenceView])
    extends ObserveModelUpdate derives Eq

case class SequenceError(obsId: Observation.Id, view: SequencesQueue[SequenceView])
    extends ObserveModelUpdate derives Eq

case class UserNotification(memo: Notification, clientId: ClientId) extends ForClient derives Eq

case class UserPromptNotification(prompt: UserPrompt, clientId: ClientId) extends ForClient
    derives Eq

case class GuideConfigUpdate(telescope: TelescopeGuideConfig) extends ObserveEvent derives Eq

case class AlignAndCalibEvent(step: Int) extends ObserveEvent derives Eq

given Eq[ObserveEvent] =
  Eq.instance {
    case (a: ConnectionOpenEvent, b: ConnectionOpenEvent)           => a === b
    case (a: ObserveModelUpdate, b: ObserveModelUpdate)             => a === b
    case (a: ServerLogMessage, b: ServerLogMessage)                 => a === b
    case (a: UserNotification, b: UserNotification)                 => a === b
    case (a: UserPromptNotification, b: UserPromptNotification)     => a === b
    case (a: GuideConfigUpdate, b: GuideConfigUpdate)               => a === b
    case (a: ObservationProgressEvent, b: ObservationProgressEvent) => a === b
    case (a: SingleActionEvent, b: SingleActionEvent)               => a === b
    case (a: AlignAndCalibEvent, b: AlignAndCalibEvent)             => a === b
    case (_: NullEvent.type, _: NullEvent.type)                     => true
    case _                                                          => false
  }

extension (e: ObserveEvent)
  def toClientEvent: Option[ObserveClientEvent] = e match {
    case ConditionsUpdated(v) =>
      Some(ObserveClientEvent(ObserveClientState(v.conditions), ObserveEventType.ConditionsUpdated))
    case _                    => None
  }
