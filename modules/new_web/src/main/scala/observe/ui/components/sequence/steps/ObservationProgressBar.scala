// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import scala.math.max
import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import react.common.*
import observe.model.ImageFileId
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import java.util.UUID
import observe.model.enums.ObservationStage
import observe.model.ObservationProgress
import java.time.Duration
import crystal.implicits.*
import crystal.Pot

trait ProgressLabel {
  def label(
    fileId:          String,
    remainingMillis: Option[Int],
    stopping:        Boolean,
    paused:          Boolean,
    stage:           ObservationStage
  ): String = {
    val durationStr = remainingMillis.foldMap { millis =>
      val remainingSecs = millis / 1000
      val remainingStr  = if (remainingSecs > 1) s"$remainingSecs seconds" else "1 second"
      s" - $remainingStr left"
    }
    val stageStr    =
      stage match {
        case ObservationStage.Preparing  => "Preparing".some
        case ObservationStage.ReadingOut => "Reading out...".some
        case _                           => None
      }

    if (paused) s"$fileId - Paused$durationStr"
    else if (stopping) s"$fileId - Stopping - Reading out..."
    else
      stageStr match {
        case Some(stage) => s"$fileId - $stage"
        case _           =>
          remainingMillis.fold(fileId) { millis =>
            if (millis > 0) s"$fileId$durationStr" else s"$fileId - Reading out..."
          }
      }
  }
}

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
// TODO Substitute for a stream hook
// protected[steps] val connect: ReactConnectProxy[Option[ObservationProgress]] =
//   ObserveCircuit.connect(ObserveCircuit.obsProgressReader[ObservationProgress](obsId, stepId))

object ObservationProgressBar:
  private type Props = ObservationProgressBar

  private val component = ScalaFnComponent
    .withHooks[Props]
    .useMemo[Unit, Pot[ObservationProgress]](())(_ =>
      ObservationProgress
        .Regular(
          obsId = Observation.Id.fromLong(1).get,
          obsName = "Test observation",
          stepId = Step.Id.fromUuid(UUID.randomUUID),
          total = Duration.ofSeconds(300),
          remaining = Duration.ofSeconds(210),
          stage = ObservationStage.Acquiring
        )
        .ready
    )
    .render((props, progress) =>
      <.div(
        // ObserveStyles.observationProgressRow,
        progress match
          case Some(ObservationProgress.Regular(_, _, _, total, remaining, stage)) =>
            // SmoothObservationProgressBar(
            //   p.fileId,
            //   total.toMilliseconds.toInt,
            //   total.toMilliseconds.toInt - max(0, remaining.toMilliseconds.toInt),
            //   p.stopping,
            //   p.paused,
            //   stage
            // )
            EmptyVdom
          case _                                                                   =>
            val msg = if (props.paused) s"${props.fileId} - Paused" else props.fileId

            // React.Fragment(
            //   Progress(
            //     indicating = true,
            //     total = 100,
            //     value = 0,
            //     color = Blue,
            //     clazz = ObserveStyles.observationProgressBar
            //   )(msg)
            // )
            EmptyVdom
      )
    )
