// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.Eq
import cats.derived.*
import lucuma.core.model.Visit
import lucuma.core.model.sequence.Dataset
import monocle.Focus
import monocle.Lens
import monocle.Optional
import observe.model.dhs.ImageFileId

protected[odb] case class RecordedVisit(visitId: Visit.Id, atom: Option[RecordedAtom] = None)
    derives Eq
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

protected[odb] case class RecordedAtom(atomId: RecordedAtomId, step: Option[RecordedStep] = None)
    derives Eq
object RecordedAtom:
  val atomId: Lens[RecordedAtom, RecordedAtomId]     = Focus[RecordedAtom](_.atomId)
  val step: Lens[RecordedAtom, Option[RecordedStep]] = Focus[RecordedAtom](_.step)
  val stepId: Optional[RecordedAtom, RecordedStepId] =
    step.some.andThen(RecordedStep.stepId)

protected[odb] case class RecordedStep(
  stepId:     RecordedStepId,
  datasetIds: DatasetIdMap = DatasetIdMap.Empty
) derives Eq
object RecordedStep:
  val stepId: Lens[RecordedStep, RecordedStepId]   = Focus[RecordedStep](_.stepId)
  val datasetIds: Lens[RecordedStep, DatasetIdMap] = Focus[RecordedStep](_.datasetIds)
