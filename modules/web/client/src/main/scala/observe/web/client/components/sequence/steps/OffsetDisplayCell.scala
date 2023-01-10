// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.steps

import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.math.Axis
import react.common._
import react.semanticui.colors._
import react.semanticui.sizes._
import observe.model.NodAndShuffleStep
import observe.model.OffsetType
import observe.model.StandardStep
import observe.model.Step
import observe.web.client.components.ObserveStyles
import observe.web.client.icons._
import observe.web.client.model.Formatting._
import observe.web.client.model.StepItems._
import observe.web.client.reusability._

/**
 * Component to display the offsets
 */
final case class OffsetsDisplayCell(
  offsetsDisplay: OffsetsDisplay,
  step:           Step
) extends ReactProps[OffsetsDisplayCell](OffsetsDisplayCell.component)

object OffsetsDisplayCell {
  type Props = OffsetsDisplayCell

  implicit val doubleReuse: Reusability[Double]      = Reusability.double(0.0001)
  implicit val ofdReuse: Reusability[OffsetsDisplay] = Reusability.derive[OffsetsDisplay]
  implicit val propsReuse: Reusability[Props]        =
    Reusability.by(p => (p.offsetsDisplay, p.step.config))

  private val guidingIcon   = IconCrosshairs.copy(color = Green, size = Large)
  private val noGuidingIcon = IconBan.size(Large)

  private def standardOffsetsRender(
    step:           StandardStep,
    offsetWidth:    Double,
    axisLabelWidth: Double
  ): VdomElement = {
    val offsetP = step.offset[OffsetType.Telescope, Axis.P]
    val offsetQ = step.offset[OffsetType.Telescope, Axis.Q]

    <.div(
      ObserveStyles.offsetsBlock,
      <.div(
        <.div(
          ObserveStyles.offsetComponent,
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
          ObserveStyles.offsetComponent,
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
      ObserveStyles.offsetsBlock,
      <.div(
        ^.width := nsNodLabelWidth.px,
        ObserveStyles.offsetsNodLabel,
        offsetNSNod[OffsetType.NSNodB]
      ),
      <.div(
        <.div(
          ObserveStyles.offsetComponent,
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
          ObserveStyles.offsetComponent,
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
        ObserveStyles.offsetsNodLabel,
        offsetNSNod[OffsetType.NSNodA]
      ),
      <.div(
        <.div(
          ObserveStyles.offsetComponent,
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
          ObserveStyles.offsetComponent,
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

  protected val component = ScalaComponent
    .builder[Props]("OffsetsDisplayCell")
    .stateless
    .render_P { p =>
      p.offsetsDisplay match {
        case OffsetsDisplay.DisplayOffsets(offsetWidth, axisLabelWidth, nsNodLabelWidth) =>
          val guiding = p.step.guiding

          <.div(
            ObserveStyles.guidingCell,
            guidingIcon.when(guiding),
            noGuidingIcon.unless(guiding),
            p.step match {
              case s: StandardStep      => standardOffsetsRender(s, offsetWidth, axisLabelWidth)
              case s: NodAndShuffleStep =>
                nodAndShuffleOffsetsRender(s, offsetWidth, axisLabelWidth, nsNodLabelWidth)
            }
          )
        case _                                                                           => <.div()
      }
    }
    .configure(Reusability.shouldComponentUpdate)
    .build
}
