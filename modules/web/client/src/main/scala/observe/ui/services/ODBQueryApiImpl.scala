// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import clue.*
import clue.data.syntax.*
import crystal.ViewF
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

// case class ODBQueryApiImpl(loadedObservations: ViewF[IO, Map[Observation.Id, LoadedObservation]])(
case class ODBQueryApiImpl()(using
  FetchClient[IO, ObservationDB],
  Logger[IO]
) extends ODBQueryApi[IO]:

  override def queryVisits(
    obsId: Observation.Id,
    from:  Option[Visit.Id]
  ): IO[Option[ExecutionVisits]] =
    // loadedObservations
    //   .zoom(Iso.id[Map[Observation.Id, LoadedObservation]].at(obsId))
    //   .toOptionView
    //   .fold(
    //     Logger[IO].error(s"refreshVisits with undefined loaded observation [$obsId]")
    //   ): loadedObs =>
    VisitQueriesGQL
      .ObservationVisits[IO]
      // .query(obsId, loadedObs.get.lastVisitId.orIgnore)
      .query(obsId, from.orIgnore)
      .raiseGraphQLErrors
      .map(_.observation.flatMap(_.execution))
    // .attempt
    // .flatMap: visits =>
    //   loadedObs.mod(_.addVisits(visits))

  override def querySequence(obsId: Observation.Id): IO[SequenceData] =
    // loadedObservations
    //   .zoom(Iso.id[Map[Observation.Id, LoadedObservation]].at(obsId))
    //   .toOptionView
    //   .fold(
    //     Logger[IO].error(s"refreshSequence with undefined loaded observation [$obsId]")
    //   ): loadedObs =>
    SequenceQueriesGQL
      .SequenceQuery[IO]
      .query(obsId)
      .raiseGraphQLErrors
      .adaptError:
        case ResponseException(errors, _) =>
          Exception(errors.map(_.message).toList.mkString("\n"))
      .map(SequenceData.fromOdbResponse)
      // .attempt
      .flatMap:
        _.fold(
          IO.raiseError(Exception(s"Execution Configuration not defined for observation [$obsId]"))
        )(IO.pure(_))
    //   _.flatMap:
    //     _.toRight:
    //       Exception:
    //         s"Execution Configuration not defined for observation [$obsId]"
    // .flatMap: sequenceData =>
    //   loadedObs.mod(_.withSequenceData(sequenceData))

  override def updateDatasetQa(datasetId: Dataset.Id, qaFields: EditableQaFields): IO[Unit] =
    VisitQueriesGQL
      .UpdateDatasetQa[IO]
      .execute(datasetId, qaFields.qaState.orUnassign, qaFields.comment.orUnassign)
      .void
      .onError:
        case e => Logger[IO].error(e)(s"Error updating dataset QA state for $datasetId")
