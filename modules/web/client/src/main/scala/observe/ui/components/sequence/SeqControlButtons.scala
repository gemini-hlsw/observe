// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

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
import observe.ui.services.SequenceApi

case class SeqControlButtons(
  obsId:     Observation.Id,
  isRunning: Boolean
) extends ReactFnProps(SeqControlButtons.component)

//  runButton(obsId, partial, nextStepToRunIdx, p.canRun)
//               .when(status.isIdle || status.isError),
//             // Cancel pause button
//             cancelPauseButton(obsId, p.canCancelPause)
//               .when(status.userStopRequested),
//             // Pause button
//             pauseButton(obsId, p.canPause)
//               .when(status.isRunning && !status.userStopRequested)

object SeqControlButtons:
  private type Props = SeqControlButtons

  private val tooltipOptions =
    TooltipOptions(position = TooltipOptions.Position.Top, showDelay = 100)

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useContext(SequenceApi.ctx)
      .render: (props, ctx, sequenceApi) =>
        import ctx.given

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
        )
