// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import cats.Eq
import cats.syntax.all.*
import monocle.Getter
import observe.model.Observation
import observe.web.client.components.sequence.steps.StepConfigTable
import observe.web.client.components.sequence.steps.StepsTable
import observe.web.client.model.*
import web.client.table.*

final case class StepsTableAndStatusFocus(
  status:           ClientStatus,
  stepsTable:       Option[StepsTableFocus],
  tableState:       TableState[StepsTable.TableColumn],
  configTableState: TableState[StepConfigTable.TableColumn]
)

object StepsTableAndStatusFocus {
  given Eq[StepsTableAndStatusFocus] =
    Eq.by(x => (x.status, x.stepsTable, x.tableState, x.configTableState))

  def stepsTableAndStatusFocusG(
    id: Observation.Id
  ): Getter[ObserveAppRootModel, StepsTableAndStatusFocus] =
    ClientStatus.clientStatusFocusL.asGetter
      .zip(
        StepsTableFocus
          .stepsTableG(id)
          .zip(
            ObserveAppRootModel
              .stepsTableStateL(id)
              .asGetter
              .zip(ObserveAppRootModel.configTableStateL)
          )
      ) >>> { case (s, (f, (a, t))) =>
      StepsTableAndStatusFocus(s, f, a.getOrElse(StepsTable.State.InitialTableState), t)
    }

}
