// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import lucuma.core.model.Observation
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Dataset
import lucuma.core.model.sequence.Step
import lucuma.core.util.NewType
import monocle.Lens
import observe.model.dhs.ImageFileId

// Atom.Ids and Step.Ids exist both in input sequences and in recorded ones.
// So, it is useful to have a new type for recorded ones in order to distinguish them.
// Visit.Ids and Dataset.Ids only exist in recorded sequences, so they don't need a new type.
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

object DatasetIdMap extends NewType[Map[ImageFileId, Dataset.Id]]:
  val Empty: DatasetIdMap                                             =
    DatasetIdMap(Map.empty)
  def at(fileId: ImageFileId): Lens[DatasetIdMap, Option[Dataset.Id]] =
    value.at(fileId)
type DatasetIdMap = DatasetIdMap.Type
