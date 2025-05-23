// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.effect.IO
import cats.syntax.all.*
import crystal.react.*
import crystal.react.hooks.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.core.util.TimeSpan
import lucuma.react.common.*
import lucuma.react.primereact.ProgressBar
import lucuma.ui.reusability.given
import observe.model.ObserveStage
import observe.model.SequenceState
import observe.model.StepProgress
import observe.model.dhs.ImageFileId
import observe.ui.ObserveStyles
import observe.ui.model.AppContext

import scala.concurrent.duration.*

/**
 * Component to wrap the progress bar
 */
case class ObservationProgressBar(
  obsId:          Observation.Id,
  stepId:         Step.Id,
  sequenceState:  SequenceState,
  exposureTime:   TimeSpan,
  progress:       Option[StepProgress],
  fileId:         ImageFileId, // TODO This can be multiple ones
  isPausedInStep: Boolean
) extends ReactFnProps(ObservationProgressBar):
  val isStatic: Boolean =
    !sequenceState.isRunning ||
      !progress.map(_.stage).contains_(ObserveStage.Exposure) ||
      isPausedInStep

  val runningProgress: Option[StepProgress] =
    progress
      .filter(_.stepId === stepId)
      .filter(_.stage =!= ObserveStage.Preparing)

  val progressRemainingTime: Option[TimeSpan] =
    runningProgress.map(_.remaining)

object ObservationProgressBar
    extends ReactFnComponent[ObservationProgressBar](props =>
      val UpdatePeriodMicros: Long     = 50000 // Smoothing parameter
      val UpdatePeriod: FiniteDuration = FiniteDuration(UpdatePeriodMicros, MICROSECONDS)
      val UpdateTimeSpan: TimeSpan     = TimeSpan.unsafeFromMicroseconds(UpdatePeriodMicros)

      for
        ctx             <- useContext(AppContext.ctx)
        remainingShown  <- useState(props.exposureTime)
        // if less than remainingShown, we keep the shown value until this one catches up
        remainingActual <- useRef(props.exposureTime)
        _               <-
          useEffectStreamWithDeps(props.isStatic): isStatic =>
            import ctx.given

            Option
              .unless(isStatic):
                fs2.Stream
                  .awakeEvery[IO](UpdatePeriod)
                  .evalMap: _ =>
                    val newRemaining = remainingActual.value -| UpdateTimeSpan
                    remainingActual.setAsync(newRemaining) >>
                      Option
                        .when(newRemaining < remainingShown.value):
                          remainingShown.setStateAsync(newRemaining)
                        .orEmpty
              .orEmpty
        _               <-
          useEffectWithDeps(props.progressRemainingTime):
            _.map: progressRemainingTime =>
              remainingActual.setAsync(progressRemainingTime) >>
                Option
                  .when(progressRemainingTime < remainingShown.value):
                    remainingShown.setStateAsync(progressRemainingTime)
                  .orEmpty
            .orEmpty
      yield props.runningProgress.fold {
        val label: String = if (props.isPausedInStep) "Paused" else "Preparing..."
        val msg: String   =
          List(s"${props.fileId.value}", label)
            .filterNot(_.isEmpty)
            .mkString(" - ")

        // Prime React's ProgressBar doesn't show a label when value is zero, so we render our own version.
        <.div(ObserveStyles.Prime.EmptyProgressBar, ObserveStyles.ObservationStepProgressBar)(
          <.div(ObserveStyles.Prime.EmptyProgressBarLabel)(msg)
        )
      } { runningProgress =>
        val elapsedMicros: Long = (props.exposureTime -| remainingShown.value).toMicroseconds
        val progress: Double    = (elapsedMicros * 100.0) / props.exposureTime.toMicroseconds

        ProgressBar(
          id = "progress",
          value = progress,
          clazz = ObserveStyles.ObservationStepProgressBar,
          displayValueTemplate = _ =>
            // This is a trick to be able to center when text fits, but align left when it doesn't, overflowing only to the right.
            // Achieved by rendering the 3 divs inside a space-between flexbox.
            React.Fragment(
              <.div,
              <.div(
                renderProgressLabel(
                  props.fileId,
                  remainingShown.value.some,
                  props.isPausedInStep,
                  runningProgress.stage
                )
              ),
              <.div
            )
        )
      }
    )
