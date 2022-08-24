// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import crystal.Pot
import crystal.implicits.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import observe.model.ImageFileId
import observe.model.ObservationProgress
import observe.model.enums.ObservationStage
import observe.ui.ObserveStyles
import react.common.*
import reactST.primereact.components.*

import java.time.Duration
import java.util.UUID
import scala.math.max

/**
 * Component to wrap the progress bar
 */
case class ObservationProgressBar(
  obsId:    Observation.Id,
  stepId:   Step.Id,
  fileId:   ImageFileId,
  stopping: Boolean,
  paused:   Boolean
) extends ReactFnProps(ObservationProgressBar.component)
// TODO Substitute for a stream hook - Progress should be a Topic somewhere
// protected[steps] val connect: ReactConnectProxy[Option[ObservationProgress]] =
//   ObserveCircuit.connect(ObserveCircuit.obsProgressReader[ObservationProgress](obsId, stepId))

object ObservationProgressBar extends ProgressLabel:
  private type Props = ObservationProgressBar

  private val component = ScalaFnComponent
    .withHooks[Props]
    .useMemo[Unit, Pot[ObservationProgress]](())(_ =>
      ObservationProgress
        .Regular(
          obsId = Observation.Id.fromLong(133742).get,
          obsName = "Test observation",
          stepId = Step.Id.fromUuid(UUID.randomUUID),
          total = Duration.ofSeconds(300),
          remaining = Duration.ofSeconds(210),
          stage = ObservationStage.Acquiring
        )
        .ready
    )
    .render((props, progress) =>
      <.div(ObserveStyles.ObservationProgressBarAndLabel)(
        // ObserveStyles.ObservationProgressRow,
        progress.value.toOption match
          case Some(ObservationProgress.Regular(_, _, _, total, remaining, stage)) =>
            // TODO Smooth Progress Bar
            // val remainingMillis = p.maxValue - s.value

            val totalMillis     = total.toMillis.toInt
            val remainingMillis = remaining.toMillis.toInt

            val progress = ((totalMillis - remainingMillis) * 100) / totalMillis

            React.Fragment(
              ProgressBar(ObserveStyles.ObservationProgressBar)
                .value(progress.toInt)
                .showValue(false),
              <.div(ObserveStyles.ObservationProgressLabel)(
                renderLabel(props.fileId, remainingMillis.some, props.stopping, props.paused, stage)
              )
            )

          // SmoothObservationProgressBar(
          //   p.fileId,
          //   total.toMilliseconds.toInt,
          //   total.toMilliseconds.toInt - max(0, remaining.toMilliseconds.toInt),
          //   p.stopping,
          //   p.paused,
          //   stage
          // )
          case _ =>
            val msg = if (props.paused) s"${props.fileId.value} - Paused" else props.fileId.value

            React.Fragment(
              ProgressBar(ObserveStyles.ObservationProgressBar)
                .value(100)
                .showValue(false),
              <.div(ObserveStyles.ObservationProgressLabel)(
                msg
              )
            )
      )
    )
