// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import cats.syntax.all.*
import clue.*
import crystal.ViewF
import lucuma.schemas.ObservationDB
import observe.queries.VisitQueriesGQL
import observe.ui.model.LoadedObservation
import org.typelevel.log4cats.Logger

case class ODBQueryApiImpl(nighttimeObservation: ViewF[IO, Option[LoadedObservation]])(using
  FetchClient[IO, ObservationDB],
  Logger[IO]
) extends ODBQueryApi[IO]:

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
