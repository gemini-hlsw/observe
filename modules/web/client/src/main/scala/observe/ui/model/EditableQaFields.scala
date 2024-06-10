// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.enums.DatasetQaState
import lucuma.schemas.model.Dataset
import monocle.Focus
import monocle.Lens

case class EditableQaFields(qaState: Option[DatasetQaState], comment: Option[NonEmptyString])

object EditableQaFields:
  val qaState: Lens[EditableQaFields, Option[DatasetQaState]] = Focus[EditableQaFields](_.qaState)
  val comment: Lens[EditableQaFields, Option[NonEmptyString]] = Focus[EditableQaFields](_.comment)

  // TODO Test
  val fromDataset: Lens[Dataset, EditableQaFields] =
    Lens[Dataset, EditableQaFields](d => EditableQaFields(d.qaState, d.comment))(f =>
      d => d.copy(qaState = f.qaState, comment = f.comment)
    )
