// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

// import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.StepEstimate
import lucuma.core.util.TimeSpan
import lucuma.react.common.*
import lucuma.ui.sequence.SequenceRowFormatters.*
import observe.ui.ObserveStyles

case class ExposureTimeCell(
  instrument:   Instrument,
  exposureTime: Option[TimeSpan],
  stepEstimate: Option[StepEstimate]
) extends ReactFnProps(ExposureTimeCell.component)

object ExposureTimeCell:
  private type Props = ExposureTimeCell

  private val component = ScalaFnComponent[Props](props =>
    // val coadds       = props.step.coAdds

    // TODO Find a better way to output math-style text
    val secondsUnits = React.Fragment(
      <.span(^.display.inlineBlock, ^.marginLeft := 5.px)("["),
      <.span(^.display.inlineBlock, ^.verticalAlign := "none", ^.fontStyle.italic)("s"),
      <.span(^.display.inlineBlock)("]")
    )

    // val displayedText: TagMod = (coadds, exposureTime) match
    //   case (c, Some(e)) if c.exists(_ > 1) =>
    //     (List(
    //       <.span(^.display.inlineBlock, s"${c.foldMap(_.show)} "),
    //       <.span(^.display.inlineBlock, ^.verticalAlign := "none", "\u2A2F"),
    //       <.span(^.display.inlineBlock, s"$e")
    //     ) ::: seconds).toTagMod
    //   case (_, Some(e))                    =>
    //     ((s"$e": VdomNode) :: seconds).toTagMod
    //   case _                               => EmptyVdom

    <.div(ObserveStyles.Centered)(
      // TODO Tooltip with estimate details
      props.exposureTime.map(time =>
        React.Fragment(FormatExposureTime(props.instrument)(time).value, secondsUnits)
      )
    )
  )
