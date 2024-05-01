// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.odb

import cats.Eq
import cats.derived.*
import lucuma.core.model.Visit
import lucuma.core.model.sequence.Dataset
import monocle.Focus
import monocle.Lens
import monocle.Optional
import observe.model.dhs.ImageFileId
import observe.model.odb.{DatasetIdMap, RecordedAtomId, RecordedStepId}
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Atom
import lucuma.core.model.sequence.Step
import lucuma.core.util.NewType
import io.circe.*
import cats.syntax.option.*

// Atom.Ids and Step.Ids exist both in input sequences and in recorded ones.
// So, it is useful to have a new type for recorded ones in order to distinguish them.
// Visit.Ids and Dataset.Ids only exist in recorded sequences, so they don't need a new type.
object RecordedAtomId extends NewType[Atom.Id]
type RecordedAtomId = RecordedAtomId.Type

object RecordedStepId extends NewType[Step.Id]
type RecordedStepId = RecordedStepId.Type

object DatasetIdMap extends NewType[Map[ImageFileId, Dataset.Id]]:
  val Empty: DatasetIdMap                                             =
    DatasetIdMap(Map.empty)
  def at(fileId: ImageFileId): Lens[DatasetIdMap, Option[Dataset.Id]] =
    value.at(fileId)
type DatasetIdMap = DatasetIdMap.Type

given KeyEncoder[ImageFileId] = KeyEncoder.instance(_.value)
given KeyDecoder[ImageFileId] = KeyDecoder.instance(ImageFileId(_).some)

protected[odb] case class RecordedStep(
  stepId:     RecordedStepId,
  datasetIds: DatasetIdMap = DatasetIdMap.Empty
) derives Eq,
      Encoder.AsObject,
      Decoder
object RecordedStep:
  val stepId: Lens[RecordedStep, RecordedStepId]   = Focus[RecordedStep](_.stepId)
  val datasetIds: Lens[RecordedStep, DatasetIdMap] = Focus[RecordedStep](_.datasetIds)

protected[odb] case class RecordedAtom(atomId: RecordedAtomId, step: Option[RecordedStep] = None)
    derives Eq,
      Encoder.AsObject,
      Decoder
object RecordedAtom:
  val atomId: Lens[RecordedAtom, RecordedAtomId]     = Focus[RecordedAtom](_.atomId)
  val step: Lens[RecordedAtom, Option[RecordedStep]] = Focus[RecordedAtom](_.step)
  val stepId: Optional[RecordedAtom, RecordedStepId] =
    step.some.andThen(RecordedStep.stepId)

protected[odb] case class RecordedVisit(visitId: Visit.Id, atom: Option[RecordedAtom] = None)
    derives Eq,
      Encoder.AsObject,
      Decoder
object RecordedVisit:
  val visitId: Lens[RecordedVisit, Visit.Id]                                      = Focus[RecordedVisit](_.visitId)
  val atom: Lens[RecordedVisit, Option[RecordedAtom]]                             = Focus[RecordedVisit](_.atom)
  val atomId: Optional[RecordedVisit, RecordedAtomId]                             =
    atom.some.andThen(RecordedAtom.atomId)
  val step: Optional[RecordedVisit, Option[RecordedStep]]                         =
    atom.some.andThen(RecordedAtom.step)
  val stepId: Optional[RecordedVisit, RecordedStepId]                             =
    atom.some.andThen(RecordedAtom.stepId)
  def datasetId(fileId: ImageFileId): Optional[RecordedVisit, Option[Dataset.Id]] =
    step.some.andThen(RecordedStep.datasetIds).andThen(DatasetIdMap.at(fileId))

object ObsRecordedIds extends NewType[Map[Observation.Id, RecordedVisit]]:
  val Empty: ObsRecordedIds                                                  =
    ObsRecordedIds(Map.empty)
  def at(obsId: Observation.Id): Lens[ObsRecordedIds, Option[RecordedVisit]] =
    value.at(obsId)
type ObsRecordedIds = ObsRecordedIds.Type
