// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.enums.GuideState
import lucuma.core.math.Offset
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.gmos.GmosNodAndShuffle
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.enums.OffsetsDisplay
import observe.ui.model.formatting.*
import react.common.*
import react.fa.IconSize

/**
 * Component to display the offsets
 */
case class OffsetsDisplayCell(
  offsetsDisplay: OffsetsDisplay,
  science:        StepConfig.Science,
  nodAndShuffle:  Option[GmosNodAndShuffle]
) extends ReactFnProps(OffsetsDisplayCell.component)

object OffsetsDisplayCell:
  private type Props = OffsetsDisplayCell

  private val GuidingIcon   = Icons.Crosshairs.copy(color = "green", size = IconSize.XL)
  private val NoGuidingIcon = Icons.Ban.withSize(IconSize.XL)

  private def standardOffsetsRender(
    offset:         Offset,
    offsetWidth:    Double,
    axisLabelWidth: Double
  ): TagMod =
    <.div(ObserveStyles.OffsetsBlock)(
      <.div(
        <.div(ObserveStyles.OffsetComponent)(
          <.div(^.width := axisLabelWidth.px)("p"),
          <.div(^.width := offsetWidth.px)(offsetAngle(offset.p.toAngle))
        ),
        <.div(ObserveStyles.OffsetComponent)(
          <.div(^.width := axisLabelWidth.px)("q"),
          <.div(^.width := offsetWidth.px)(offsetAngle(offset.q.toAngle))
        )
      )
    )

  private def nodAndShuffleOffsetsRender(
    nodAndShuffle:   GmosNodAndShuffle,
    width:           Double,
    axisLabelWidth:  Double,
    nsNodLabelWidth: Double
  ): VdomNode =
    <.div(ObserveStyles.OffsetsBlock)(
      <.div(ObserveStyles.OffsetsNodLabel, ^.width := nsNodLabelWidth.px)("B"),
      <.div(
        <.div(ObserveStyles.OffsetComponent)(
          <.div(^.width := axisLabelWidth.px)("p"),
          <.div(^.width := width.px)(offsetAngle(nodAndShuffle.posB.p.toAngle))
        ),
        <.div(ObserveStyles.OffsetComponent)(
          <.div(^.width := axisLabelWidth.px)("q"),
          <.div(^.width := width.px)(offsetAngle(nodAndShuffle.posB.q.toAngle))
        )
      ),
      <.div(ObserveStyles.OffsetsNodLabel, ^.width := nsNodLabelWidth.px)("A"),
      <.div(
        <.div(ObserveStyles.OffsetComponent)(
          <.div(^.width := axisLabelWidth.px)("p"),
          <.div(^.width := width.px)(offsetAngle(nodAndShuffle.posA.p.toAngle))
        ),
        <.div(ObserveStyles.OffsetComponent)(
          <.div(^.width := axisLabelWidth.px)("q"),
          <.div(^.width := width.px)(offsetAngle(nodAndShuffle.posA.q.toAngle))
        )
      )
    )

  private val component =
    ScalaFnComponent[Props]: props =>
      props.offsetsDisplay match
        case OffsetsDisplay.DisplayOffsets(offsetWidth, axisLabelWidth, nsNodLabelWidth) =>
          val guiding = props.science.guiding == GuideState.Enabled

          <.div(ObserveStyles.GuidingCell)(
            GuidingIcon.when(guiding),
            NoGuidingIcon.unless(guiding),
            props.nodAndShuffle.fold(
              standardOffsetsRender(props.science.offset, offsetWidth, axisLabelWidth)
            )(
              nodAndShuffleOffsetsRender(_, offsetWidth, axisLabelWidth, nsNodLabelWidth)
            )
          )
        case _                                                                           => EmptyVdom
