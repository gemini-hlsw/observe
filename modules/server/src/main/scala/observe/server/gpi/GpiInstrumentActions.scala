// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gpi

import cats.syntax.all._
import fs2.Stream
import org.typelevel.log4cats.Logger
import observe.engine.ParallelActions
import observe.engine.Result
import observe.server.InstrumentActions
import observe.server.ObserveActions
import observe.server.ObserveEnvironment
import observe.server.StepType
import cats.effect.Temporal

/**
 * Gpi needs different actions for A&C
 */
class GpiInstrumentActions[F[_]: Logger: Temporal] extends InstrumentActions[F] {

  override def observationProgressStream(
    env: ObserveEnvironment[F]
  ): Stream[F, Result[F]] =
    ObserveActions.observationProgressStream(env)

  override def observeActions(
    env: ObserveEnvironment[F]
  ): List[ParallelActions[F]] =
    if (env.stepType === StepType.AlignAndCalib) {
      Nil
    } else {
      InstrumentActions.defaultInstrumentActions[F].observeActions(env)
    }

  override def runInitialAction(stepType: StepType): Boolean =
    stepType =!= StepType.AlignAndCalib

}
