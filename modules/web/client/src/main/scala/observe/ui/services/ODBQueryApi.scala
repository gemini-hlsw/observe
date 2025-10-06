// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import cats.effect.Sync
import japgolly.scalajs.react.React
import japgolly.scalajs.react.feature.Context
import lucuma.core.model.sequence.Dataset
import lucuma.schemas.model.ExecutionVisits
import lucuma.schemas.model.Visit
import lucuma.ui.sequence.SequenceData
import observe.model.Observation
import observe.ui.model.EditableQaFields

import scala.annotation.unused

trait ODBQueryApi[F[_]: Sync]:
  def queryVisits(
    @unused obsId: Observation.Id,
    @unused from:  Option[Visit.Id]
  ): F[Option[ExecutionVisits]] =
    Sync[F].delay(???)

  def querySequence(@unused obsId: Observation.Id): F[SequenceData] =
    Sync[F].delay(???)

  def updateDatasetQa(@unused datasetId: Dataset.Id, @unused qa: EditableQaFields): F[Unit] =
    Sync[F].delay(???)

object ODBQueryApi:
  // Default value noop implementations with warning
  val ctx: Context[ODBQueryApi[IO]] = React.createContext(new ODBQueryApi[IO] {})
