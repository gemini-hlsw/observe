// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.events

import cats.*
import cats.syntax.all.*
import io.circe.KeyDecoder
import io.circe.KeyEncoder
import lucuma.core.enums.Breakpoint
import lucuma.core.model.Observation
import observe.model.ExecutionState
import observe.model.SequenceView
import observe.model.SequencesQueue

protected[events] given KeyEncoder[Observation.Id] = _.toString
protected[events] given KeyDecoder[Observation.Id] = Observation.Id.parse(_)

extension (v: SequencesQueue[SequenceView])
  def sequencesState: Map[Observation.Id, ExecutionState] =
    v.sessionQueue.map(o => o.obsId -> o.executionState).toMap

extension (q: SequenceView)
  def executionState: ExecutionState =
    ExecutionState(
      q.status,
      q.metadata.observer,
      q.sequenceType,
      q.steps,
      q.runningStep.flatMap(_.id),
      None,
      q.stepResources,
      q.systemOverrides,
      q.steps.mapFilter(s => if (s.breakpoint === Breakpoint.Enabled) s.id.some else none).toSet,
      q.pausedStep
    )
