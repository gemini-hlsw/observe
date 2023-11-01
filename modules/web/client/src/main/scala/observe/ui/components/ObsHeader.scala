// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.*
import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.*
import lucuma.react.fa.IconSize
import lucuma.react.primereact.Button
import lucuma.react.primereact.TooltipOptions
import observe.model.Observation
import observe.model.enums.RunOverride
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.AppContext
import observe.ui.model.ObsSummary
import observe.ui.services.SequenceApi

case class ObsHeader(
  obsId:       Observation.Id,
  observation: ObsSummary,
  isRunning:   Boolean
) extends ReactFnProps(ObsHeader.component)

// TODO Pass seqOperations and disable/animate play button

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
              clazz = ObserveStyles.PlayButton |+| ObserveStyles.ObsSummaryButton,
              icon =
                if (props.isRunning)
                  Icons.CircleNotch.withFixedWidth().withSize(IconSize.LG).withSpin()
                else
                  Icons.Play.withFixedWidth().withSize(IconSize.LG),
              tooltip = "Start/Resume sequence",
              tooltipOptions = tooltipOptions,
              onClick = sequenceApi.start(props.obsId, RunOverride.Override).runAsync,
              disabled = props.isRunning
            ),
            s"${props.observation.title} [${props.obsId}]"
          ),
          <.div(ObserveStyles.ObsSummaryDetails)(
            <.span(props.observation.configurationSummary),
            <.span(props.observation.constraintsSummary)
          )
        )
