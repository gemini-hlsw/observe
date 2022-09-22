// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.math.Axis
import react.common._
import react.semanticui.colors._
import react.semanticui.sizes._
import observe.model.NodAndShuffleStep
import observe.model.ExecutionStep
import observe.ui.model.enums.OffsetsDisplay
import observe.model.StandardStep
import observe.model.OffsetType
import observe.ui.model.extensions.*
import observe.ui.ObserveStyles
import observe.ui.Icons
import react.fa.IconSize
import observe.ui.model.formatting.*

/**
 * Component to display the offsets
 */
case class OffsetsDisplayCell(
  offsetsDisplay: OffsetsDisplay,
  step:           ExecutionStep
) extends ReactFnProps(OffsetsDisplayCell.component)

object OffsetsDisplayCell {
  private type Props = OffsetsDisplayCell

  private val GuidingIcon   = Icons.Crosshairs.copy(color = "green", size = IconSize.LG)
  private val NoGuidingIcon = Icons.Ban.size(IconSize.LG)

  private def standardOffsetsRender(
    step:           StandardStep,
    offsetWidth:    Double,
    axisLabelWidth: Double
  ): VdomElement = {
    val offsetP = step.offset[OffsetType.Telescope, Axis.P]
    val offsetQ = step.offset[OffsetType.Telescope, Axis.Q]

    <.div(
      // ObserveStyles.offsetsBlock,
      <.div(
        <.div(
          // ObserveStyles.offsetComponent,
          <.div(
            ^.width := axisLabelWidth.px,
            offsetAxis[Axis.P]
          ),
          <.div(
            ^.width := offsetWidth.px,
            offsetAngle(offsetP.toAngle)
          )
        ),
        <.div(
          // ObserveStyles.offsetComponent,
          <.div(
            ^.width := axisLabelWidth.px,
            offsetAxis[Axis.Q]
          ),
          <.div(
            ^.width := offsetWidth.px,
            offsetAngle(offsetQ.toAngle)
          )
        )
      )
    )
  }

  private def nodAndShuffleOffsetsRender(
    step:            NodAndShuffleStep,
    width:           Double,
    axisLabelWidth:  Double,
    nsNodLabelWidth: Double
  ): VdomElement = {
    val offsetBP = step.offset[OffsetType.NSNodB, Axis.P]
    val offsetBQ = step.offset[OffsetType.NSNodB, Axis.Q]
    val offsetAP = step.offset[OffsetType.NSNodA, Axis.P]
    val offsetAQ = step.offset[OffsetType.NSNodA, Axis.Q]

    <.div(
      // ObserveStyles.offsetsBlock,
      <.div(
        ^.width := nsNodLabelWidth.px,
        // ObserveStyles.offsetsNodLabel,
        offsetNSNod[OffsetType.NSNodB]
      ),
      <.div(
        <.div(
          // ObserveStyles.offsetComponent,
          <.div(
            ^.width := axisLabelWidth.px,
            offsetAxis[Axis.P]
          ),
          <.div(
            ^.width := width.px,
            offsetAngle(offsetBP.toAngle)
          )
        ),
        <.div(
          // ObserveStyles.offsetComponent,
          <.div(
            ^.width := axisLabelWidth.px,
            offsetAxis[Axis.Q]
          ),
          <.div(
            ^.width := width.px,
            offsetAngle(offsetBQ.toAngle)
          )
        )
      ),
      <.div(
        ^.width := nsNodLabelWidth.px,
        // ObserveStyles.offsetsNodLabel,
        offsetNSNod[OffsetType.NSNodA]
      ),
      <.div(
        <.div(
          // ObserveStyles.offsetComponent,
          <.div(
            ^.width := axisLabelWidth.px,
            offsetAxis[Axis.P]
          ),
          <.div(
            ^.width := width.px,
            offsetAngle(offsetAP.toAngle)
          )
        ),
        <.div(
          // ObserveStyles.offsetComponent,
          <.div(
            ^.width := axisLabelWidth.px,
            offsetAxis[Axis.Q]
          ),
          <.div(
            ^.width := width.px,
            offsetAngle(offsetAQ.toAngle)
          )
        )
      )
    )
  }

  protected val component =
    ScalaFnComponent[Props](props =>
      props.offsetsDisplay match
        case OffsetsDisplay.DisplayOffsets(offsetWidth, axisLabelWidth, nsNodLabelWidth) =>
          val guiding = props.step.guiding

          <.div(
            // ObserveStyles.guidingCell,
            GuidingIcon.when(guiding),
            NoGuidingIcon.unless(guiding),
            props.step match
              case s @ StandardStep(_, _, _, _, _, _, _, _)         =>
                standardOffsetsRender(s, offsetWidth, axisLabelWidth)
              case s @ NodAndShuffleStep(_, _, _, _, _, _, _, _, _) =>
                nodAndShuffleOffsetsRender(s, offsetWidth, axisLabelWidth, nsNodLabelWidth)
          )
        case _                                                                           => EmptyVdom
    )
}
