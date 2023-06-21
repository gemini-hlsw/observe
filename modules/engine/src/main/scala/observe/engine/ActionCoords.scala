// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.Eq
import observe.model.Observation
import observe.model.StepId

final case class ActionIndex(self: Long) extends AnyVal
object ActionIndex {
  given Eq[ActionIndex] = Eq.by(_.self)
}

final case class ExecutionIndex(self: Long) extends AnyVal
object ExecutionIndex {
  given Eq[ExecutionIndex] = Eq.by(_.self)
}

final case class ActionCoordsInSeq(stepId: StepId, execIdx: ExecutionIndex, actIdx: ActionIndex)
object ActionCoordsInSeq {
  given Eq[ActionCoordsInSeq] =
    Eq.by(x => (x.stepId, x.execIdx, x.actIdx))
}

/*
 * Class to hold the coordinates of an Action inside the engine state
 */
final case class ActionCoords(sid: Observation.Id, actCoords: ActionCoordsInSeq)
object ActionCoords {
  given Eq[ActionCoords] = Eq.by(x => (x.sid, x.actCoords))
}
