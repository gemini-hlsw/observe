// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import observe.model.enums.StepState
import observe.model.enums.ExecutionStepType
import observe.model.ExecutionStep
import observe.ui.model.extensions.*
import react.common.*
import react.primereact.*
import lucuma.core.syntax.display.*
import observe.ui.ObserveStyles

case class ObjectTypeCell(instrument: Instrument, step: ExecutionStep)
    extends ReactFnProps(ObjectTypeCell.component)

object ObjectTypeCell:
  private type Props = ObjectTypeCell

  private val component = ScalaFnComponent[Props](props =>
    <.div(ObserveStyles.StepTypeCell)(
      props.step
        .stepType(props.instrument)
        .map { st =>
          val stepTypeColor = st match
            case _ if props.step.status === StepState.Completed => ObserveStyles.StepTypeCompleted
            case ExecutionStepType.Object                       => ObserveStyles.StepTypeObject
            case ExecutionStepType.Arc                          => ObserveStyles.StepTypeArc
            case ExecutionStepType.Flat                         => ObserveStyles.StepTypeFlat
            case ExecutionStepType.Bias                         => ObserveStyles.StepTypeBias
            case ExecutionStepType.Dark                         => ObserveStyles.StepTypeDark
            case ExecutionStepType.Calibration                  => ObserveStyles.StepTypeCalibration
            case ExecutionStepType.AlignAndCalib                => ObserveStyles.StepTypeAlignAndCalib
            case ExecutionStepType.NodAndShuffle                => ObserveStyles.StepTypeNodAndShuffle
            case ExecutionStepType.NodAndShuffleDark            => ObserveStyles.StepTypeNodAndShuffleDark

          Tag(value = st.shortName).withMods(ObserveStyles.StepTypeTag |+| stepTypeColor)
        }
        .whenDefined
    )
  )
