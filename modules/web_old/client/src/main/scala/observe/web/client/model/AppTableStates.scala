// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import cats.Eq
import monocle.Lens
import monocle.function.At.*
import observe.model.Observation
import observe.model.*
import observe.web.client.components.SessionQueueTable
import observe.web.client.components.queue.CalQueueTable
import observe.web.client.components.sequence.steps.StepConfigTable
import observe.web.client.components.sequence.steps.StepsTable
import web.client.table.*

/**
 * Store the state of each of the app tables
 */
final case class AppTableStates(
  sessionQueueTable: TableState[SessionQueueTable.TableColumn],
  stepConfigTable:   TableState[StepConfigTable.TableColumn],
  stepsTables:       Map[Observation.Id, TableState[StepsTable.TableColumn]],
  queueTables:       Map[QueueId, TableState[CalQueueTable.TableColumn]]
)

object AppTableStates {

  val Initial: AppTableStates = AppTableStates(
    SessionQueueTable.State.InitialTableState,
    StepConfigTable.InitialTableState,
    Map.empty,
    Map.empty
  )

  given Eq[AppTableStates] =
    Eq.by(x => (x.sessionQueueTable, x.stepConfigTable, x.stepsTables, x.queueTables))

  def stepsTableAtL(
    id: Observation.Id
  ): Lens[AppTableStates, Option[TableState[StepsTable.TableColumn]]] =
    Focus[AppTableStates](_.stepsTables).andThen(at(id))

  def queueTableAtL(
    id: QueueId
  ): Lens[AppTableStates, Option[TableState[CalQueueTable.TableColumn]]] =
    Focus[AppTableStates](_.queueTables).andThen(at(id))
}
