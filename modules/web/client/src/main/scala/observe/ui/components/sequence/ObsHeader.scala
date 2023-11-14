// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.*
import observe.model.Observation
import observe.ui.ObserveStyles
import observe.ui.model.ObsSummary

case class ObsHeader(
  obsId:       Observation.Id,
  observation: ObsSummary,
  isRunning:   Boolean
) extends ReactFnProps(ObsHeader.component)

object ObsHeader:
  private type Props = ObsHeader

  private val component =
    ScalaFnComponent[Props]: props =>
      <.div(ObserveStyles.ObsSummary)(
        <.div(ObserveStyles.ObsSummaryTitle)(
          SeqControlButtons(props.obsId, props.isRunning),
          s"${props.observation.title} [${props.obsId}]"
        ),
        <.div(ObserveStyles.ObsSummaryDetails)(
          <.span(props.observation.configurationSummary),
          <.span(props.observation.constraintsSummary)
        )
      )
