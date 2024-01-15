// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import lucuma.core.model.sequence.Step
import observe.engine.Result.*
import observe.model.ClientId
import observe.model.Observation

/**
 * Events generated internally by the Engine.
 */
sealed trait SystemEvent extends Product with Serializable

object SystemEvent {
  final case class Completed[R <: RetVal](id: Observation.Id, stepId: Step.Id, i: Int, r: OK[R])
      extends SystemEvent
  final case class StopCompleted[R <: RetVal](
    id:     Observation.Id,
    stepId: Step.Id,
    i:      Int,
    r:      OKStopped[R]
  ) extends SystemEvent
  final case class Aborted[R <: RetVal](
    id:     Observation.Id,
    stepId: Step.Id,
    i:      Int,
    r:      OKAborted[R]
  ) extends SystemEvent
  final case class PartialResult[R <: PartialVal](
    sid:    Observation.Id,
    stepId: Step.Id,
    i:      Int,
    r:      Partial[R]
  ) extends SystemEvent
  final case class Paused(id: Observation.Id, i: Int, r: Result.Paused) extends SystemEvent
  final case class Failed(id: Observation.Id, i: Int, e: Result.Error)  extends SystemEvent
  final case class Busy(id: Observation.Id, clientId: ClientId)         extends SystemEvent
  final case class BreakpointReached(id: Observation.Id)                extends SystemEvent
  final case class Executed(id: Observation.Id)                         extends SystemEvent
  final case class Executing(id: Observation.Id)                        extends SystemEvent
  final case class Finished(id: Observation.Id)                         extends SystemEvent
  case object Null                                                      extends SystemEvent

  // Single action commands
  final case class SingleRunCompleted[R <: RetVal](actionCoords: ActionCoords, r: OK[R])
      extends SystemEvent
  final case class SingleRunFailed(actionCoords: ActionCoords, e: Result.Error) extends SystemEvent
}
