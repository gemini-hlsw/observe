// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.*
import observe.model.Observation
import observe.ui.ObserveStyles
import observe.ui.model.ObsSummary
import lucuma.react.primereact.Button
import lucuma.react.primereact.TooltipOptions
import observe.ui.Icons
import lucuma.react.fa.IconSize
import observe.ui.services.SequenceApi
import crystal.react.*
import observe.ui.model.AppContext
import observe.model.enums.RunOverride

case class ObsHeader(
  obsId:       Observation.Id,
  observation: ObsSummary
) extends ReactFnProps(ObsHeader.component)

object ObsHeader:
  private type Props = ObsHeader

  private val tooltipOptions =
    TooltipOptions(position = TooltipOptions.Position.Top, showDelay = 100)

  val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useContext(SequenceApi.ctx)
      .render: (props, ctx, sequenceApi) =>
        import ctx.given

        <.div(ObserveStyles.ObsSummary)(
          <.div(ObserveStyles.ObsSummaryTitle)(
            Button(
              clazz = ObserveStyles.PlayButton,
              icon = Icons.Play.withFixedWidth().withSize(IconSize.LG),
              tooltip = "Start/Resume sequence",
              tooltipOptions = tooltipOptions,
              onClick = sequenceApi.start(props.obsId, RunOverride.Override).runAsync
            ),
            s"${props.observation.title} [${props.obsId}]"
          ),
          <.div(ObserveStyles.ObsSummaryDetails)(
            <.span(props.observation.configurationSummary),
            <.span(props.observation.constraintsSummary)
          )
        )
