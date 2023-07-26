// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.syntax.all.*
import mouse.all.*
import observe.engine
import observe.model.NodAndShuffleStep.PendingObserveCmd
import observe.model.*
import observe.model.enums.*
import observe.server.StepsView.*
import observe.server.*
import observe.server.gmos.GmosController.Config.*

final class GmosStepsView[F[_]] extends StepsView[F] {
  override def stepView(
    stepg:         SequenceGen.StepGen[F],
    step:          engine.Step[F],
    altCfgStatus:  List[(Resource, ActionStatus)],
    pendingObsCmd: Option[PendingObserveCmd]
  ): Step = {
    val nodAndShuffle: Option[GmosController.Config.NSConfig.NodAndShuffle] = stepg.genData match {
      case Gmos.GmosStatusGen(ns: NSConfig.NodAndShuffle) => ns.some
      case _                                              => none
    }

    nodAndShuffle
      .map { e =>
        val status       = step.status
        val configStatus =
          if (status.runningOrComplete) {
            stepConfigStatus(step)
          } else {
            altCfgStatus
          }
        val runningState = (status === StepState.Running).option {
          val nsPartials = step.executions
            .filter { case l =>
              l.count(_.kind === ActionType.Observe) > 0
            }
            .foldMap(_.foldMap(_.state.partials))
            .filter {
              case _: NSPartial => true
              case _            => false
            }
          nsPartials.headOption.collect { case s: NSPartial =>
            NSRunningState(s.ongoingAction, s.sub)
          }
        }

        NodAndShuffleStep(
          id = step.id,
          status = status,
          breakpoint = step.breakpoint.self,
          skip = step.skipMark.self,
          configStatus = configStatus,
          nsStatus = NodAndShuffleStatus(
            observeStatus(step.executions),
            e.totalExposureTime,
            e.nodExposureTime,
            e.cycles,
            runningState.flatten
          ),
          fileId = StepsView
            .fileId(step.executions)
            .orElse(stepg.some.collect { case SequenceGen.CompletedStepGen(_, _, fileId, _) =>
              fileId
            }.flatten),
          pendingObserveCmd =
            (observeStatus(step.executions) === ActionStatus.Running).option(pendingObsCmd).flatten
        )
      }
      .getOrElse(
        defaultStepsView.stepView(stepg, step, altCfgStatus, none)
      )
  }

}

object GmosStepsView {
  def stepsView[F[_]]: StepsView[F] = new GmosStepsView[F]
}
