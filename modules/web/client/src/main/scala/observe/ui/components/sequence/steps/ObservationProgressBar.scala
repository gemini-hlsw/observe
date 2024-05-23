// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
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
import observe.ui.model.reusability.given

import scala.concurrent.duration.*

/**
 * Component to wrap the progress bar
 */
case class ObservationProgressBar(
  obsId:          Observation.Id,
  stepId:         Step.Id,
  sequenceState:  SequenceState,
  progress:       Option[StepProgress],
  fileId:         ImageFileId, // TODO This can be multiple ones
  isStopping:     Boolean,
  isPausedInStep: Boolean
) extends ReactFnProps(ObservationProgressBar.component):
  val isStatic: Boolean =
    !sequenceState.isRunning ||
      !props.progress.map(_.stage).contains_(ObserveStage.Acquiring) ||
      isStopping ||
      isPausedInStep

  val runningProgress: Option[StepProgress] =
    props.progress
      .filter(_.stepId === props.stepId)
      .filter(_.stage =!= ObserveStage.Preparing)

object ObservationProgressBar extends ProgressLabel:
  private type Props = ObservationProgressBar

  // Smoothing parameters
  private val UpdatePeriodMicros: Long     = 50000
  private val UpdatePeriod: FiniteDuration = FiniteDuration(UpdatePeriodMicros, MICROSECONDS)
  // The following value was found by trial and error to provide the smoothest experience.
  private val UpdateMicros: Long           = 58000

  private def computeProgressValues(
    runningProgress: Option[StepProgress],
    extra:           Long
  ): (Double, Long) =
    runningProgress match
      case Some(StepProgress.Regular(_, total, remaining, _)) =>
        val totalMicros: Long   = total.toMicroseconds
        val elapsedMicros: Long = totalMicros - remaining.toMicroseconds + extra
        ((elapsedMicros * 100.0) / totalMicros, totalMicros - elapsedMicros)
      case _                                                  => (0.0, 0L)

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useState(0L) // extra - Microprogress increased between server updates to smooth progress
      .useEffectWithDepsBy((props, _, _) => props.progress.map(_.remaining)): (props, _, extra) =>
        _ => extra.setState(0L)
      .useEffectStreamWithDepsBy((props, _, _) => props.isStatic): (_, ctx, extra) =>
        isStatic =>
          import ctx.given

          Option
            .unless(isStatic):
              fs2.Stream
                .awakeEvery[IO](UpdatePeriod)
                .evalMap: _ =>
                  extra.modStateAsync: previous =>
                    previous + UpdateMicros
            .orEmpty
      // (progress, remainingMicros)
      .useRefBy((props, _, extra) => computeProgressValues(props.runningProgress, extra.value))
      .useEffectWithDepsBy((props, _, extra, _) => (props.runningProgress, extra.value)):
        (_, _, _, progress) =>
          (runningProgress, extra) =>
            val (newProgress, remainingMicros) = computeProgressValues(runningProgress, extra)
            if (newProgress > progress.value._1)
              progress.set((newProgress, remainingMicros))
            else
              Callback.empty
      .render: (props, _, _, progress) =>
        props.runningProgress.fold {
          val msg: String =
            List(s"${props.fileId.value}", if (props.isPausedInStep) "Paused" else "Preparing...")
              .filterNot(_.isEmpty)
              .mkString(" - ")

          // Prime React's ProgressBar doesn't show a label when value is zero, so we render our own version.
          <.div(ObserveStyles.Prime.EmptyProgressBar, ObserveStyles.ObservationProgressBar)(
            <.div(ObserveStyles.Prime.EmptyProgressBarLabel)(msg)
          )
        } { runningProgress =>
          ProgressBar(
            id = "progress",
            value = progress.value._1,
            clazz = ObserveStyles.ObservationProgressBar,
            displayValueTemplate = _ =>
              // This is a trick to be able to center when text fits, but align left when it doesn't, overflowing only to the right.
              // Achieved by rendering the 3 divs inside a space-between flexbox.
              React.Fragment(
                <.div,
                <.div(
                  renderLabel(
                    props.fileId,
                    progress.value._2.some,
                    props.isStopping,
                    props.isPausedInStep,
                    runningProgress.stage
                  )
                ),
                <.div
              )
          )
        }
