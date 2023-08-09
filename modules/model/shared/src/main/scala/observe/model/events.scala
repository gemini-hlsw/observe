// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.*
import cats.syntax.all.*
import observe.model.dhs.ImageFileId
import observe.model.enums.*

import java.time.Instant

object events {
  given Eq[Instant] = Eq.fromUniversalEquals

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

  final case class ObservationProgressEvent(progress: Progress) extends ObserveEvent

  object ObservationProgressEvent {
    given Eq[ObservationProgressEvent] = Eq.by(_.progress)
  }

  final case class ServerLogMessage(level: ServerLogLevel, timestamp: Instant, msg: String)
      extends ObserveEvent
  object ServerLogMessage {
    private implicit val instantOrder: Order[Instant] =
      Order.by(_.getNano)
    given Order[ServerLogMessage]                     =
      Order.by(x => (x.level, x.timestamp, x.msg))
  }

  case object NullEvent extends ObserveEvent

  final case class ConnectionOpenEvent(
    userDetails:   Option[UserDetails],
    clientId:      ClientId,
    serverVersion: String
  ) extends ObserveEvent

  object ConnectionOpenEvent {
    given Eq[ConnectionOpenEvent] =
      Eq.by(x => (x.userDetails, x.clientId, x.serverVersion))
  }

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

  final case class SequenceStart(
    obsId:  Observation.Id,
    stepId: StepId,
    view:   SequencesQueue[SequenceView]
  ) extends ObserveModelUpdate

  object SequenceStart {
    given Eq[SequenceStart] =
      Eq.by(x => (x.obsId, x.stepId, x.view))
  }

  final case class StepExecuted(obsId: Observation.Id, view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object StepExecuted {
    given Eq[StepExecuted] =
      Eq.by(x => (x.obsId, x.view))
  }

  final case class FileIdStepExecuted(fileId: ImageFileId, view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object FileIdStepExecuted {
    given Eq[FileIdStepExecuted] =
      Eq.by(x => (x.fileId, x.view))
  }

  final case class SequenceCompleted(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate

  object SequenceCompleted {
    given Eq[SequenceCompleted] =
      Eq.by(_.view)
  }

  final case class SequenceLoaded(obsId: Observation.Id, view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object SequenceLoaded {
    given Eq[SequenceLoaded] =
      Eq.by(x => (x.obsId, x.view))
  }

  final case class SequenceUnloaded(obsId: Observation.Id, view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object SequenceUnloaded {
    given Eq[SequenceUnloaded] =
      Eq.by(x => (x.obsId, x.view))
  }

  final case class StepBreakpointChanged(view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object StepBreakpointChanged {
    given Eq[StepBreakpointChanged] =
      Eq.by(_.view)
  }

  final case class OperatorUpdated(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate

  object OperatorUpdated {
    given Eq[OperatorUpdated] =
      Eq.by(_.view)
  }

  final case class QueueUpdated(op: QueueManipulationOp, view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object QueueUpdated {
    given Eq[QueueUpdated] =
      Eq.by(x => (x.op, x.view))
  }

  final case class SingleActionEvent(op: SingleActionOp) extends ObserveEvent

  object SingleActionEvent {
    given Eq[SingleActionEvent] =
      Eq.by(_.op)
  }

  final case class LoadSequenceUpdated(
    i:        Instrument,
    obsId:    Observation.Id,
    view:     SequencesQueue[SequenceView],
    clientId: ClientId
  ) extends ObserveModelUpdate

  object LoadSequenceUpdated {
    given Eq[LoadSequenceUpdated] =
      Eq.by(x => (x.i, x.obsId, x.view, x.clientId))
  }

  final case class ClearLoadedSequencesUpdated(view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object ClearLoadedSequencesUpdated {
    given Eq[ClearLoadedSequencesUpdated] =
      Eq.by(_.view)
  }

  final case class ObserverUpdated(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate

  object ObserverUpdated {
    given Eq[ObserverUpdated] =
      Eq.by(_.view)
  }

  final case class OverridesUpdated(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate

  object OverridesUpdated {
    given Eq[OverridesUpdated] =
      Eq.by(_.view)
  }

  final case class ConditionsUpdated(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate

  object ConditionsUpdated {
    given Eq[ConditionsUpdated] =
      Eq.by(_.view)
  }

  final case class StepSkipMarkChanged(view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object StepSkipMarkChanged {
    given Eq[StepSkipMarkChanged] =
      Eq.by(_.view)
  }

  final case class SequencePauseRequested(view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object SequencePauseRequested {
    given Eq[SequencePauseRequested] =
      Eq.by(_.view)
  }

  final case class SequencePauseCanceled(obsId: Observation.Id, view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object SequencePauseCanceled {
    given Eq[SequencePauseCanceled] =
      Eq.by(x => (x.obsId, x.view))
  }

  final case class SequenceRefreshed(view: SequencesQueue[SequenceView], clientId: ClientId)
      extends ObserveModelUpdate
      with ForClient

  object SequenceRefreshed {
    given Eq[SequenceRefreshed] =
      Eq.by(x => (x.view, x.clientId))
  }

  final case class ActionStopRequested(view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object ActionStopRequested {
    given Eq[ActionStopRequested] =
      Eq.by(_.view)
  }

  final case class SequenceStopped(obsId: Observation.Id, view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object SequenceStopped {
    given Eq[SequenceStopped] =
      Eq.by(x => (x.obsId, x.view))
  }

  final case class SequenceAborted(obsId: Observation.Id, view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object SequenceAborted {
    given Eq[SequenceAborted] =
      Eq.by(x => (x.obsId, x.view))
  }

  final case class SequenceUpdated(view: SequencesQueue[SequenceView]) extends ObserveModelUpdate

  object SequenceUpdated {
    given Eq[SequenceUpdated] =
      Eq.by(_.view)
  }

  final case class SequencePaused(obsId: Observation.Id, view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object SequencePaused {
    given Eq[SequencePaused] =
      Eq.by(x => (x.obsId, x.view))
  }

  final case class ExposurePaused(obsId: Observation.Id, view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object ExposurePaused {
    given Eq[ExposurePaused] =
      Eq.by(x => (x.obsId, x.view))
  }

  final case class SequenceError(obsId: Observation.Id, view: SequencesQueue[SequenceView])
      extends ObserveModelUpdate

  object SequenceError {
    given Eq[SequenceError] =
      Eq.by(x => (x.obsId, x.view))
  }

  final case class UserNotification(memo: Notification, clientId: ClientId) extends ForClient

  object UserNotification {
    given Eq[UserNotification] =
      Eq.by(x => (x.memo, x.clientId))
  }

  final case class UserPromptNotification(prompt: UserPrompt, clientId: ClientId) extends ForClient

  object UserPromptNotification {
    given Eq[UserPromptNotification] =
      Eq.by(x => (x.prompt, x.clientId))
  }

  final case class GuideConfigUpdate(telescope: TelescopeGuideConfig) extends ObserveEvent

  object GuideConfigUpdate {
    given Eq[GuideConfigUpdate] =
      Eq.by(_.telescope)
  }

  final case class AlignAndCalibEvent(step: Int) extends ObserveEvent

  object AlignAndCalibEvent {
    given Eq[AlignAndCalibEvent] =
      Eq.by(_.step)
  }

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

}
