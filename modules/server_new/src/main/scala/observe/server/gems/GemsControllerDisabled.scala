// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gems

import cats.Applicative
import cats.syntax.all.*
import lucuma.core.model.GemsConfig
import observe.server.gems.Gems.GemsWfsState
import observe.server.overrideLogMessage
import observe.server.tcs.Gaos
import observe.server.tcs.Gaos.PauseConditionSet
import observe.server.tcs.Gaos.PauseResume
import org.typelevel.log4cats.Logger

class GemsControllerDisabled[F[_]: Logger: Applicative] extends GemsController[F] {
  override def pauseResume(
    pauseReasons:  PauseConditionSet,
    resumeReasons: Gaos.ResumeConditionSet
  )(cfg: GemsConfig): F[Gaos.PauseResume[F]] =
    PauseResume(
      overrideLogMessage("GeMS", "pause AO loops").some,
      overrideLogMessage("GeMS", "resume AO loops").some
    ).pure[F]

  override val stateGetter: GemsWfsState[F] = GemsWfsState.allOff
}
