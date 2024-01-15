// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.engine

import cats.Eq
import cats.derived.*
import lucuma.core.model.sequence.Step
import lucuma.core.util.NewType
import observe.model.Observation

object ActionIndex extends NewType[Long]
type ActionIndex = ActionIndex.Type

object ExecutionIndex extends NewType[Long]
type ExecutionIndex = ExecutionIndex.Type

case class ActionCoordsInSeq(stepId: Step.Id, execIdx: ExecutionIndex, actIdx: ActionIndex)
    derives Eq

/*
 * Class to hold the coordinates of an Action inside the engine state
 */
case class ActionCoords(sid: Observation.Id, actCoords: ActionCoordsInSeq) derives Eq
