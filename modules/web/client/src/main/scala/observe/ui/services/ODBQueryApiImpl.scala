// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.services

import cats.effect.IO
import cats.syntax.all.*
import clue.*
import crystal.ViewF
import lucuma.schemas.ObservationDB
import lucuma.schemas.model.AtomRecord
import lucuma.schemas.model.ExecutionVisits
import lucuma.schemas.model.StepRecord
import lucuma.schemas.model.Visit
import lucuma.schemas.model.enums.StepExecutionState
import monocle.Traversal
import observe.queries.VisitQueriesGQL
import observe.ui.model.LoadedObservation
import org.typelevel.log4cats.Logger

case class ODBQueryApiImpl(nighttimeObservation: ViewF[IO, Option[LoadedObservation]])(using
  FetchClient[IO, ObservationDB],
  Logger[IO]
) extends ODBQueryApi[IO]:

  private val gmosNorthVisitsSteps: Traversal[ExecutionVisits, List[StepRecord.GmosNorth]] =
    ExecutionVisits.gmosNorth
      .andThen(ExecutionVisits.GmosNorth.visits.each)
      .andThen(Visit.GmosNorth.atoms.each)
      .andThen(AtomRecord.GmosNorth.steps)

  private val gmosSouthVisitsSteps: Traversal[ExecutionVisits, List[StepRecord.GmosSouth]] =
    ExecutionVisits.gmosSouth
      .andThen(ExecutionVisits.GmosSouth.visits.each)
      .andThen(Visit.GmosSouth.atoms.each)
      .andThen(AtomRecord.GmosSouth.steps)

  private val gmosNorthRemoveOngoingSteps: ExecutionVisits => ExecutionVisits =
    gmosNorthVisitsSteps.modify(_.filter(_.executionState =!= StepExecutionState.Ongoing))

  private val gmosSouthRemoveOngoingSteps: ExecutionVisits => ExecutionVisits =
    gmosSouthVisitsSteps.modify(_.filter(_.executionState =!= StepExecutionState.Ongoing))

  private val removeOngoingSteps: ExecutionVisits => ExecutionVisits =
    gmosNorthRemoveOngoingSteps >>> gmosSouthRemoveOngoingSteps

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
          loadedObs.mod:
            _.withVisits:
              // Ongoing step is shown from current atom, not from visits, so we remove it.
              visits.map:
                _.map(removeOngoingSteps)
