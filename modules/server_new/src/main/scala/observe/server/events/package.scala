// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.events

import observe.engine.Result
import observe.engine.SystemEvent
import observe.engine.UserEvent
import observe.server.SeqEvent

extension (e: SeqEvent)
  def isModelUpdate: Boolean =
    import SeqEvent.*
    e match
      case SetOperator(_, _)             => true
      case SetObserver(_, _, _)          => true
      case SetTcsEnabled(_, _, _)        => true
      case SetGcalEnabled(_, _, _)       => true
      case SetInstrumentEnabled(_, _, _) => true
      case SetDhsEnabled(_, _, _)        => true
      case AddLoadedSequence(_, _, _, _) => true
      case ClearLoadedSequences(_)       => true
      case SetConditions(_, _)           => true
      case SetImageQuality(_, _)         => true
      case SetWaterVapor(_, _)           => true
      case SetSkyBackground(_, _)        => true
      case SetCloudCover(_, _)           => true
      case LoadSequence(_)               => true
      case UnloadSequence(_)             => true
      case UpdateQueueAdd(_, _)          => true
      case UpdateQueueRemove(_, _, _, _) => true
      case UpdateQueueMoved(_, _, _, _)  => true
      case UpdateQueueClear(_)           => true
      case StartQueue(_, _, _)           => true
      case StopQueue(_, _)               => true
      case StartSysConfig(_, _, _)       => true
      case SequenceStart(_, _)           => true
      case SequencesStart(_)             => true
      case _                             => false

extension [F[_], S, U](e: UserEvent[F, S, U])
  def isModelUpdate: Boolean =
    import UserEvent.*
    e match
      case UserEvent.Start(id, _, _)         => true
      case UserEvent.Pause(_, _)             => true
      case UserEvent.CancelPause(id, _)      => true
      case UserEvent.Breakpoints(_, _, _, _) => true
      case UserEvent.Poll(_)                 => true
      case UserEvent.ActionStop(_, _)        => true
      case UserEvent.ActionResume(_, _, _)   => true
      case _                                 => false

extension (e: SystemEvent)
  def isModelUpdate: Boolean =
    import SystemEvent.*
    e match
      case SystemEvent.Completed(_, _, _, _)      => true
      case SystemEvent.StopCompleted(id, _, _, _) => true
      case SystemEvent.Aborted(id, _, _, _)       => true
      case SystemEvent.PartialResult(_, _, _, _)  => true
      case SystemEvent.Failed(id, _, _)           => true
      case SystemEvent.Executed(_)                => true
      case SystemEvent.Executing(_)               => true
      case SystemEvent.Finished(_)                => true
      case SystemEvent.Paused(id, _, _)           => true
      case SystemEvent.BreakpointReached(id)      => true
      case _                                      => false
