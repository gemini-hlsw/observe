// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import cats.syntax.all.*
import clue.*
import clue.data.syntax.*
import crystal.ViewF
import lucuma.core.model.Visit
import lucuma.core.model.sequence.Dataset
import lucuma.schemas.ObservationDB
import lucuma.schemas.model.ExecutionVisits
import lucuma.schemas.odb.SequenceQueriesGQL
import observe.queries.VisitQueriesGQL
import observe.ui.DefaultErrorPolicy
import observe.ui.model.EditableQaFields
import observe.ui.model.LoadedObservation
import org.typelevel.log4cats.Logger

case class ODBQueryApiImpl(nighttimeObservation: ViewF[IO, Option[LoadedObservation]])(using
  FetchClient[IO, ObservationDB],
  Logger[IO]
) extends ODBQueryApi[IO]:

  private def lastVisitId(lo: LoadedObservation): Option[Visit.Id] =
    lo.visits.toOption.flatten.map:
      case ExecutionVisits.GmosNorth(visits) => visits.last.id
      case ExecutionVisits.GmosSouth(visits) => visits.last.id

  override def refreshNighttimeVisits: IO[Unit] =
    nighttimeObservation.toOptionView.fold(
      Logger[IO].error("refreshNighttimeVisits with undefined loaded observation")
    ): loadedObs =>
      VisitQueriesGQL
        .ObservationVisits[IO]
        .query(loadedObs.get.obsId, lastVisitId(loadedObs.get).orIgnore)
        .map(_.observation.flatMap(_.execution))
        .attempt
        .flatMap: visits =>
          loadedObs.mod(_.addVisits(visits))

  override def refreshNighttimeSequence: IO[Unit] =
    nighttimeObservation.toOptionView.fold(
      Logger[IO].error("refreshNighttimeSequence with undefined loaded observation")
    ): loadedObs =>
      SequenceQueriesGQL
        .SequenceQuery[IO]
        .query(loadedObs.get.obsId)
        .adaptError:
          case ResponseException(errors, _) =>
            Exception(errors.map(_.message).toList.mkString("\n"))
        .map(_.observation.map(_.execution.config))
        .attempt
        .map:
          _.flatMap:
            _.toRight:
              Exception:
                s"Execution Configuration not defined for observation [${loadedObs.get.obsId}]"
        .flatMap: config =>
          nighttimeObservation.mod(_.map(_.withConfig(config)))

  override def updateDatasetQa(datasetId: Dataset.Id, qaFields: EditableQaFields): IO[Unit] =
    VisitQueriesGQL
      .UpdateDatasetQa[IO]
      .execute(datasetId, qaFields.qaState.orUnassign, qaFields.comment.orUnassign)
      .void
      .onError: e =>
        Logger[IO].error(e)(s"Error updating dataset QA state for $datasetId")
