// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import clue.*
import clue.data.syntax.*
import lucuma.core.model.sequence.Dataset
import lucuma.schemas.ObservationDB
import lucuma.schemas.model.ExecutionVisits
import lucuma.schemas.model.Visit
import lucuma.schemas.odb.SequenceQueriesGQL
import lucuma.ui.sequence.SequenceData
import observe.model.Observation
import observe.queries.VisitQueriesGQL
import observe.ui.model.EditableQaFields
import org.typelevel.log4cats.Logger

case class ODBQueryApiImpl()(using FetchClient[IO, ObservationDB], Logger[IO])
    extends ODBQueryApi[IO]:

  override def queryVisits(
    obsId: Observation.Id,
    from:  Option[Visit.Id]
  ): IO[Option[ExecutionVisits]] =
    VisitQueriesGQL
      .ObservationVisits[IO]
      .query(obsId, from.orIgnore)
      .raiseGraphQLErrors
      .map(_.observation.flatMap(_.execution))

  override def querySequence(obsId: Observation.Id): IO[SequenceData] =
    SequenceQueriesGQL
      .SequenceQuery[IO]
      .query(obsId)
      .raiseGraphQLErrors
      .adaptError:
        case ResponseException(errors, _) =>
          Exception(errors.map(_.message).toList.mkString("\n"))
      .map(SequenceData.fromOdbResponse)
      .flatMap:
        _.fold(
          IO.raiseError(Exception(s"Execution Configuration not defined for observation [$obsId]"))
        )(IO.pure(_))

  override def updateDatasetQa(datasetId: Dataset.Id, qaFields: EditableQaFields): IO[Unit] =
    VisitQueriesGQL
      .UpdateDatasetQa[IO]
      .execute(datasetId, qaFields.qaState.orUnassign, qaFields.comment.orUnassign)
      .void
      .onError:
        case e => Logger[IO].error(e)(s"Error updating dataset QA state for $datasetId")
