// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.odb

import cats.Eq
import cats.derived.*
import lucuma.core.model.Visit
import monocle.Focus
import monocle.Lens

protected[odb] case class RecordedIds(
  visitId: Option[Visit.Id],
  atomId:  Option[RecordedAtomId],
  stepId:  Option[RecordedStepId]
) derives Eq

object RecordedIds:
  val visitId: Lens[RecordedIds, Option[Visit.Id]]      = Focus[RecordedIds](_.visitId)
  val atomId: Lens[RecordedIds, Option[RecordedAtomId]] = Focus[RecordedIds](_.atomId)
  val stepId: Lens[RecordedIds, Option[RecordedStepId]] = Focus[RecordedIds](_.stepId)
