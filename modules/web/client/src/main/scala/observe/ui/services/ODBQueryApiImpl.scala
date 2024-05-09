// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import cats.syntax.all.*
import clue.*
import crystal.ViewF
import lucuma.schemas.ObservationDB
import lucuma.schemas.odb.SequenceSQL
import observe.queries.VisitQueriesGQL
import observe.ui.model.LoadedObservation
import org.typelevel.log4cats.Logger

case class ODBQueryApiImpl(nighttimeObservation: ViewF[IO, Option[LoadedObservation]])(using
  FetchClient[IO, ObservationDB],
  Logger[IO]
) extends ODBQueryApi[IO]:

  // TODO REMEMBER LAST VISIT AND REQUERY STARTING THERE!!!
  override def refreshNighttimeVisits: IO[Unit] =
    nighttimeObservation.toOptionView.fold(
      Logger[IO].error("refreshNighttimeVisits with undefined loaded observation")
    ): loadedObs =>
      VisitQueriesGQL
        .ObservationVisits[IO]
        .query(loadedObs.get.obsId)(ErrorPolicy.IgnoreOnData)
        .map(_.observation.flatMap(_.execution))
        .attempt
        .flatMap: visits =>
          loadedObs.mod(_.withVisits(visits))

  override def refreshNighttimeSequence: IO[Unit] =
    nighttimeObservation.toOptionView.fold(
      Logger[IO].error("refreshNighttimeSequence with undefined loaded observation")
    ): loadedObs =>
      SequenceSQL
        .SequenceQuery[IO]
        .query(loadedObs.get.obsId)(ErrorPolicy.RaiseAlways)
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
