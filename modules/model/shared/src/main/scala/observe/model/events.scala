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

sealed trait ObserveEvent       extends Product with Serializable derives Eq
sealed trait ObserveModelUpdate extends ObserveEvent derives Eq {
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

extension (e: ObserveEvent)
  def toClientEvent: Option[ObserveClientEvent] = e match {
    case ConditionsUpdated(v) =>
      Some(ObserveClientEvent(ObserveClientState(v.conditions), ObserveEventType.ConditionsUpdated))
    case _                    => None
  }
