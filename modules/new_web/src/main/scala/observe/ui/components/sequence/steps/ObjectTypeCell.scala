// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
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
    println(
      props.step
        .stepType(props.instrument)
    )
    <.div(
      props.step
        .stepType(props.instrument)
        .map { st =>
          val stepTypeColor = st match
            case _ if props.step.status === StepState.Completed => ObserveStyles.TypeCompleted
            case ExecutionStepType.Object                       => ObserveStyles.TypeObject
            // case ExecutionStepType.Arc                          => Violet
            // case ExecutionStepType.Flat                         => Grey
            // case ExecutionStepType.Bias                         => Teal
            // case ExecutionStepType.Dark                         => Black
            // case ExecutionStepType.Calibration                  => Blue
            // case ExecutionStepType.AlignAndCalib                => Brown
            // case ExecutionStepType.NodAndShuffle                => Olive
            // case ExecutionStepType.NodAndShuffleDark            => Black
            case _                                              => Css.Empty

          // val component =
          Tag(value = st.shortName).withMods(ObserveStyles.ObjectType |+| stepTypeColor)
          //     .withMods(^.fontSize := "0.78571429rem", ^.background := stepTypeColor)

          // println(
          //   component.modifiers.toTagMod.asInstanceOf[TagMod.Composite].mods.toList.map(_.toJs)
          // )

          // component
          // Tag(color = stepTypeColor)(st.show)
        }
        .whenDefined
    )
  )
