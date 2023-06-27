// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import observe.model.ExecutionStep
import observe.ui.ObserveStyles
import observe.ui.model.extensions.*
import react.common.*

case class ExposureTimeCell(step: ExecutionStep, instrument: Instrument)
    extends ReactFnProps(ExposureTimeCell.component)

object ExposureTimeCell:
  private type Props = ExposureTimeCell

  private val component = ScalaFnComponent[Props](props =>
    val exposureTime = props.step.exposureTimeS(props.instrument)
    val coadds       = props.step.coAdds

    // TODO Find a better way to output math-style text
    val seconds = List(
      <.span(^.display.inlineBlock, ^.marginLeft    := 5.px, "["),
      <.span(^.display.inlineBlock, ^.verticalAlign := "none", ^.fontStyle.italic, "s"),
      <.span(^.display.inlineBlock, "]")
    )

    val displayedText: TagMod = (coadds, exposureTime) match
      case (c, Some(e)) if c.exists(_ > 1) =>
        (List(
          <.span(^.display.inlineBlock, s"${c.foldMap(_.show)} "),
          <.span(^.display.inlineBlock, ^.verticalAlign := "none", "\u2A2F"),
          <.span(^.display.inlineBlock, s"$e")
        ) ::: seconds).toTagMod
      case (_, Some(e))                    =>
        ((s"$e": VdomNode) :: seconds).toTagMod
      case _                               => EmptyVdom

    <.div(ObserveStyles.Centered)(displayedText)
  )
