// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import lucuma.core.model.Observation
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Step
import lucuma.core.util.NewType
import monocle.Lens

object RecordedAtomId extends NewType[Atom.Id]
type RecordedAtomId = RecordedAtomId.Type

object RecordedStepId extends NewType[Step.Id]
type RecordedStepId = RecordedStepId.Type

object ObsRecordedIds extends NewType[Map[Observation.Id, RecordedVisit]]:
  val Empty: ObsRecordedIds                                                  =
    ObsRecordedIds(Map.empty)
  def at(obsId: Observation.Id): Lens[ObsRecordedIds, Option[RecordedVisit]] =
    value.at(obsId)
type ObsRecordedIds = ObsRecordedIds.Type
