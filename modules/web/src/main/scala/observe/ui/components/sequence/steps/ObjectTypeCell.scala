// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.syntax.display.*
import lucuma.react.common.*
import lucuma.react.primereact.*
import observe.model.enums.ExecutionStepType
import observe.ui.ObserveStyles

case class ObjectTypeCell(stepType: ExecutionStepType, isFinished: Boolean)
    extends ReactFnProps(ObjectTypeCell.component)

object ObjectTypeCell:
  private type Props = ObjectTypeCell

  private val component = ScalaFnComponent[Props]: props =>
    val stepTypeColor = props.stepType match
      case _ if props.isFinished               => ObserveStyles.StepTypeCompleted
      case ExecutionStepType.Object            => ObserveStyles.StepTypeObject
      case ExecutionStepType.Arc               => ObserveStyles.StepTypeArc
      case ExecutionStepType.Flat              => ObserveStyles.StepTypeFlat
      case ExecutionStepType.Bias              => ObserveStyles.StepTypeBias
      case ExecutionStepType.Dark              => ObserveStyles.StepTypeDark
      case ExecutionStepType.Calibration       => ObserveStyles.StepTypeCalibration
      case ExecutionStepType.AlignAndCalib     => ObserveStyles.StepTypeAlignAndCalib
      case ExecutionStepType.NodAndShuffle     => ObserveStyles.StepTypeNodAndShuffle
      case ExecutionStepType.NodAndShuffleDark => ObserveStyles.StepTypeNodAndShuffleDark

    <.div(ObserveStyles.StepTypeCell)(
      Tag(value = props.stepType.shortName).withMods(ObserveStyles.StepTypeTag |+| stepTypeColor)
    )
