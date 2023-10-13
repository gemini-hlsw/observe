// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import lucuma.core.enums.CloudExtinction
import lucuma.core.enums.ImageQuality
import lucuma.core.enums.SkyBackground
import lucuma.core.enums.WaterVapor
import lucuma.core.model.User
import observe.model.ClientId
import observe.model.Conditions
import observe.model.Notification
import observe.model.Observation
import observe.model.Observer
import observe.model.Operator
import observe.model.QueueId
import observe.model.StepId
import observe.model.SubsystemEnabled
import observe.model.UserPrompt
import observe.model.enums.*

sealed trait SeqEvent extends Product with Serializable

object SeqEvent {
  case class SetOperator(name: Operator, user: Option[User])                      extends SeqEvent
  case class SetObserver(id: Observation.Id, user: Option[User], name: Observer)  extends SeqEvent
  case class SetTcsEnabled(id: Observation.Id, user: Option[User], enabled: SubsystemEnabled)
      extends SeqEvent
  case class SetGcalEnabled(id: Observation.Id, user: Option[User], enabled: SubsystemEnabled)
      extends SeqEvent
  case class SetInstrumentEnabled(
    id:      Observation.Id,
    user:    Option[User],
    enabled: SubsystemEnabled
  ) extends SeqEvent
  case class SetDhsEnabled(id: Observation.Id, user: Option[User], enabled: SubsystemEnabled)
      extends SeqEvent
  case class SetConditions(conditions: Conditions, user: Option[User])            extends SeqEvent
  case class LoadSequence(sid: Observation.Id)                                    extends SeqEvent
  case class UnloadSequence(id: Observation.Id)                                   extends SeqEvent
  case class AddLoadedSequence(
    instrument: Instrument,
    obsId:      Observation.Id,
    user:       User,
    clientId:   ClientId
  ) extends SeqEvent
  case class ClearLoadedSequences(user: Option[User])                             extends SeqEvent
  case class SetImageQuality(iq: ImageQuality, user: Option[User])                extends SeqEvent
  case class SetWaterVapor(wv: WaterVapor, user: Option[User])                    extends SeqEvent
  case class SetSkyBackground(wv: SkyBackground, user: Option[User])              extends SeqEvent
  case class SetCloudCover(cc: CloudExtinction, user: Option[User])               extends SeqEvent
  case class NotifyUser(memo: Notification, clientID: ClientId)                   extends SeqEvent
  case class RequestConfirmation(prompt: UserPrompt, cid: ClientId)               extends SeqEvent
  case class StartQueue(
    qid:         QueueId,
    clientID:    ClientId,
    startedSeqs: List[(Observation.Id, StepId)]
  ) extends SeqEvent
  case class StopQueue(qid: QueueId, clientID: ClientId)                          extends SeqEvent
  case class UpdateQueueAdd(qid: QueueId, seqs: List[Observation.Id])             extends SeqEvent
  case class UpdateQueueRemove(
    qid:         QueueId,
    seqs:        List[Observation.Id],
    pos:         List[Int],
    startedSeqs: List[(Observation.Id, StepId)]
  ) extends SeqEvent
  case class UpdateQueueMoved(qid: QueueId, cid: ClientId, oid: Observation.Id, pos: Int)
      extends SeqEvent
  case class UpdateQueueClear(qid: QueueId)                                       extends SeqEvent
  case class StartSysConfig(obsId: Observation.Id, stepId: StepId, res: Resource) extends SeqEvent
  case class Busy(obsId: Observation.Id, cid: ClientId)                           extends SeqEvent
  case class SequenceStart(sid: Observation.Id, stepId: StepId)                   extends SeqEvent
  case class SequencesStart(startedSeqs: List[(Observation.Id, StepId)])          extends SeqEvent
  case class ResourceBusy(
    obsId:    Observation.Id,
    stepId:   StepId,
    res:      Resource,
    clientID: ClientId
  ) extends SeqEvent
  case object NullSeqEvent                                                        extends SeqEvent
}
