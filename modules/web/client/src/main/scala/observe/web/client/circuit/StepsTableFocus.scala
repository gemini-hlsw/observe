// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import cats._
import cats.syntax.all._
import monocle.Getter
import monocle.macros.Lenses
import observe.model.Observation
import observe.model._
import observe.model.enum._
import observe.web.client.components.sequence.steps.StepsTable
import observe.web.client.model.ModelOps._
import observe.web.client.model._
import web.client.table._

@Lenses
final case class StepsTableFocus(
  idName:              Observation.IdName,
  instrument:          Instrument,
  state:               SequenceState,
  steps:               List[Step],
  stepConfigDisplayed: Option[StepId],
  nextStepToRun:       Option[StepId],
  selectedStep:        Option[StepId],
  runningStep:         Option[RunningStep],
  isPreview:           Boolean,
  tableState:          TableState[StepsTable.TableColumn],
  tabOperations:       TabOperations
)

object StepsTableFocus {
  implicit val eq: Eq[StepsTableFocus] =
    Eq.by(x =>
      (x.idName,
       x.instrument,
       x.state,
       x.steps,
       x.stepConfigDisplayed,
       x.nextStepToRun,
       x.selectedStep,
       x.runningStep,
       x.isPreview,
       x.tableState,
       x.tabOperations
      )
    )

  def stepsTableG(
    id: Observation.Id
  ): Getter[ObserveAppRootModel, Option[StepsTableFocus]] =
    ObserveAppRootModel.sequencesOnDisplayL
      .andThen(SequencesOnDisplay.tabG(id))
      .zip(ObserveAppRootModel.stepsTableStateL(id).asGetter) >>> {
      case (Some(ObserveTabActive(tab, _)), ts) =>
        val sequence = tab.sequence
        StepsTableFocus(
          sequence.idName,
          sequence.metadata.instrument,
          sequence.status,
          sequence.steps,
          tab.stepConfigDisplayed,
          sequence.nextStepToRun,
          tab.selectedStep
            .orElse(sequence.nextStepToRun), // start with the nextstep selected
          sequence.runningStep,
          tab.isPreview,
          ts.getOrElse(StepsTable.State.InitialTableState),
          tab.tabOperations
        ).some
      case _                                    => none
    }
}
