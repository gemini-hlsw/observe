// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.handlers

import scala.concurrent.duration.Duration

import cats.syntax.all._
import diode.ActionHandler
import diode.ActionResult
import diode.ModelRW
import observe.model.NSObservationProgress
import observe.model.Observation
import observe.model.ObservationProgress
import observe.model.ObserveStage
import observe.model.Progress
import observe.model.Step
import observe.model.enum.ActionStatus
import observe.model.events.ObservationProgressEvent
import observe.model.events.ObserveModelUpdate
import observe.model.events.SequenceAborted
import observe.model.events.SequenceError
import observe.model.events.StepExecuted
import observe.web.client.actions._
import observe.web.client.model._
import observe.web.client.model.lenses.sequenceStepT
import observe.web.client.model.lenses.sequenceViewT
import squants.time.Time

/**
 * Handles updates to obs progress
 */
class ObservationsProgressStateHandler[M](modelRW: ModelRW[M, AllObservationsProgressState])
    extends ActionHandler(modelRW)
    with Handlers[M, AllObservationsProgressState] {

  // In N&S, whenever a nod finishes (and the observation continues), the instruments show:
  // 1) Remaining time reaches 0.
  // 2) Acquiring channel is set to 0. (Which means we now receive stage === Idle.
  // 3) Stage index increases.
  // 4) Remaining time resets.
  // 5) Preparing channel is set to 1.
  // 6) Preparing channel is set to 0.
  // 7) Acquiring channel is set to 1.
  // 8) Remaning time continues.
  //
  // Therefore, we should not adjust the current stage index while Acquiring is off (we receive Idle).
  // Furthermore, when we have a stage === ReadingOut, we force progress variable to complete
  //   (by setting remaining = 0 and copying subexposure variables from previous progress).
  private def adjustProgress(newProgress: Progress)(oldProgress: Progress): Progress =
    newProgress match {
      case nsProgress: NSObservationProgress =>
        nsProgress.stage match {
          case ObserveStage.ReadingOut =>
            oldProgress match {
              case oldNSProgress: NSObservationProgress =>
                nsProgress.copy(remaining = Time(Duration.Zero), sub = oldNSProgress.sub)
              case _                                    => nsProgress // This would be an odd case
            }
          case ObserveStage.Idle       => oldProgress
          case ObserveStage.Preparing  =>
            oldProgress match {
              case oldNSProgress: NSObservationProgress =>
                oldNSProgress.copy(stage = ObserveStage.Preparing)
              case oldProgress: ObservationProgress     =>
                oldProgress.copy(stage = ObserveStage.Preparing)
            }
          case _                       => newProgress // Only advance progress when stage === Acquiring.
        }
      case _                                 => newProgress
    }

  private def resetStepProgress(
    e:         ObserveModelUpdate,
    obsId:     Observation.Id,
    condition: Step => Boolean = _ => true
  ): ActionResult[M] = {
    val upd =
      for {
        obs     <- sequenceViewT.find(_.idName.id === obsId)(e)
        curSId  <- obs.runningStep.flatMap(_.id)
        curStep <- sequenceStepT.find(_.id === curSId)(obs)
      } yield
        if (condition(curStep)) {
          updatedL(
            AllObservationsProgressState
              .progressByIdL(obsId, curSId)
              .replace(none)
          )
        } else {
          noChange
        }

    upd.getOrElse(noChange)
  }

  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case ServerMessage(ObservationProgressEvent(e)) =>
      updatedL(
        AllObservationsProgressState
          .progressByIdL(e.obsIdName.id, e.stepId)
          .modify(_.map(adjustProgress(e)).orElse(e.some))
      )

    case ServerMessage(e @ SequenceAborted(obsId, _)) =>
      resetStepProgress(e, obsId)

    case ServerMessage(e @ SequenceError(obsId, _)) =>
      resetStepProgress(e, obsId)

    // Remove the progress once the step completes
    case ServerMessage(e @ StepExecuted(obsId, _)) =>
      resetStepProgress(
        e,
        obsId,
        curStep =>
          Step.observeStatus
            .getOption(curStep)
            .exists(_ === ActionStatus.Completed) &&
            !curStep.isObservePaused
      )
  }
}
