// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.util.TimeSpan
import lucuma.react.common.*
import lucuma.react.primereact.ProgressBar
import observe.model.ObserveStage
import observe.model.StepProgress
import observe.model.dhs.ImageFileId
import observe.ui.ObserveStyles

/**
 * Component to wrap the progress bar
 */
case class ObservationProgressBar(
  obsId:          Observation.Id,
  stepId:         Step.Id,
  progress:       Option[StepProgress],
  fileId:         ImageFileId, // TODO This can be multiple ones
  isStopping:     Boolean,
  isPausedInStep: Boolean
) extends ReactFnProps(ObservationProgressBar.component)
// TODO Substitute for a stream hook - Progress should be a Topic somewhere
// protected[steps] val connect: ReactConnectProxy[Option[ObservationProgress]] =
//   ObserveCircuit.connect(ObserveCircuit.obsProgressReader[ObservationProgress](obsId, stepId))

object ObservationProgressBar extends ProgressLabel:
  private type Props = ObservationProgressBar

  private val component = ScalaFnComponent[Props]: props =>
    props.progress.filter(_.stepId === props.stepId).filter(_.stage != ObserveStage.Preparing) match
      case Some(StepProgress.Regular(_, total, remaining, stage)) =>
        // TODO Smooth Progress Bar
        // val remainingMillis = p.maxValue - s.value

        val totalMillis     = total.toMilliseconds.toInt
        val remainingMillis = remaining.toMilliseconds.toInt

        val progress = ((totalMillis - remainingMillis) * 100) / totalMillis

        ProgressBar(
          // mode = ProgressBar.Mode.Indeterminate, // TODO Remove when we have progress
          value = progress.toInt, // TODO Reinstate when we have progress
          clazz = ObserveStyles.ObservationProgressBar,
          displayValueTemplate = _ =>
            // This is a trick to be able to center when text fits, but align left when it doesn't, overflowing only to the right.
            // Achieved by rendering the 3 divs inside a space-between flexbox.
            React.Fragment(
              <.div,
              <.div(
                renderLabel(
                  props.fileId,
                  remainingMillis.some,
                  props.isStopping,
                  props.isPausedInStep,
                  stage
                )
              ),
              <.div
            )
        )

      case _ =>
        val msg: String =
          List(s"${props.fileId.value}", if (props.isPausedInStep) "Paused" else "Preparing...")
            .filterNot(_.isEmpty)
            .mkString(" - ")

        // Prime React's ProgressBar doesn't show a label when value is zero, so we render our own version.
        <.div(ObserveStyles.Prime.EmptyProgressBar, ObserveStyles.ObservationProgressBar)(
          <.div(ObserveStyles.Prime.EmptyProgressBarLabel)(msg)
        )
