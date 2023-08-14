// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.math.Offset
import lucuma.core.model.sequence.gmos.GmosNodAndShuffle
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.enums.OffsetsDisplay
import lucuma.ui.sequence.SequenceRowFormatters.*
import lucuma.react.common.*
import lucuma.react.fa.IconSize

/**
 * Component to display the offsets
 */
case class OffsetsDisplayCell(
  offsetsDisplay: OffsetsDisplay,
  offset:         Offset,
  hasGuiding:     Boolean,
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
          <.div(^.width := offsetWidth.px)(FormatOffsetP(offset.p).value)
        ),
        <.div(ObserveStyles.OffsetComponent)(
          <.div(^.width := axisLabelWidth.px)("q"),
          <.div(^.width := offsetWidth.px)(FormatOffsetQ(offset.q).value)
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
          <.div(^.width := width.px)(FormatOffsetP(nodAndShuffle.posB.p).value)
        ),
        <.div(ObserveStyles.OffsetComponent)(
          <.div(^.width := axisLabelWidth.px)("q"),
          <.div(^.width := width.px)(FormatOffsetQ(nodAndShuffle.posB.q).value)
        )
      ),
      <.div(ObserveStyles.OffsetsNodLabel, ^.width := nsNodLabelWidth.px)("A"),
      <.div(
        <.div(ObserveStyles.OffsetComponent)(
          <.div(^.width := axisLabelWidth.px)("p"),
          <.div(^.width := width.px)(FormatOffsetP(nodAndShuffle.posA.p).value)
        ),
        <.div(ObserveStyles.OffsetComponent)(
          <.div(^.width := axisLabelWidth.px)("q"),
          <.div(^.width := width.px)(FormatOffsetQ(nodAndShuffle.posA.q).value)
        )
      )
    )

  private val component =
    ScalaFnComponent[Props]: props =>
      props.offsetsDisplay match
        case OffsetsDisplay.DisplayOffsets(offsetWidth, axisLabelWidth, nsNodLabelWidth) =>
          <.div(ObserveStyles.GuidingCell)(
            GuidingIcon.when(props.hasGuiding),
            NoGuidingIcon.unless(props.hasGuiding),
            props.nodAndShuffle.fold(
              standardOffsetsRender(props.offset, offsetWidth, axisLabelWidth)
            )(
              nodAndShuffleOffsetsRender(_, offsetWidth, axisLabelWidth, nsNodLabelWidth)
            )
          )
        case _                                                                           => EmptyVdom
