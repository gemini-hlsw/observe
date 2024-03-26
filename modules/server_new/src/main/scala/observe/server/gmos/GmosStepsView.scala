// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.syntax.all.*
import lucuma.core.enums.Instrument
import mouse.all.*
import observe.engine
import observe.model.*
import observe.model.enums.*
import observe.server.*
import observe.server.StepsView.*
import observe.server.gmos.GmosController.Config.*

class GmosStepsView[F[_]] extends StepsView[F] {
  override def stepView(
    stepg:         SequenceGen.StepGen[F],
    step:          engine.EngineStep[F],
    altCfgStatus:  List[(Resource | Instrument, ActionStatus)],
    pendingObsCmd: Option[PendingObserveCmd]
  ): ObserveStep = {
    val nodAndShuffle: Option[GmosController.Config.NsConfig.NodAndShuffle] = stepg.genData match {
      case Gmos.GmosStatusGen(ns: NsConfig.NodAndShuffle) => ns.some
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
            NsRunningState(s.ongoingAction, s.sub)
          }
        }

        ObserveStep.NodAndShuffle(
          id = step.id,
          instConfig = stepg.instConfig,
          stepConfig = stepg.config,
          status = status,
          breakpoint = step.breakpoint,
          skip = step.skipMark.value,
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
            .orElse(stepg.some.collect { case SequenceGen.CompletedStepGen(_, _, fileId, _, _, _) =>
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
