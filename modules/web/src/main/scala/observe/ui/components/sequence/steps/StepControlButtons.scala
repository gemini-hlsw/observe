// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.react.common.*
import observe.model.SequenceState
import observe.model.enums.{Instrument => ObserveInstrument}
import observe.model.operations.*
import observe.ui.components.sequence.ControlButtons
import observe.ui.model.TabOperations

/**
 * Contains the control buttons like stop/abort at the row level
 */
final case class StepControlButtons(
  obsId:           Observation.Id,
  instrument:      Instrument,
  sequenceState:   SequenceState,
  stepId:          Step.Id,
  isObservePaused: Boolean,
  isMultiLevel:    Boolean,
  tabOperations:   TabOperations
) extends ReactFnProps(StepControlButtons.component)

object StepControlButtons:
  private type Props = StepControlButtons

  protected val component = ScalaFnComponent[Props]: props =>
    ObserveInstrument
      .fromCoreInstrument(props.instrument)
      .map: instrument =>
        ControlButtons(
          props.obsId,
          instrument.operations(
            OperationLevel.Observation,
            props.isObservePaused,
            props.isMultiLevel
          ),
          props.sequenceState,
          props.stepId,
          props.isObservePaused,
          props.tabOperations
        ): VdomNode
