// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gems

import lucuma.core.model.GemsConfig
import observe.server.gems.Gems.GemsWfsState
import observe.server.tcs.Gaos.PauseConditionSet
import observe.server.tcs.Gaos.PauseResume
import observe.server.tcs.Gaos.ResumeConditionSet

trait GemsController[F[_]] {

  def pauseResume(pauseReasons: PauseConditionSet, resumeReasons: ResumeConditionSet)(
    cfg: GemsConfig
  ): F[PauseResume[F]]

  val stateGetter: GemsWfsState[F]

}
