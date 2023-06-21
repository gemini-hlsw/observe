// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gems

import cats.Applicative
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import observe.server.gems.Gems.Cwfs1DetectorState
import observe.server.gems.Gems.Cwfs2DetectorState
import observe.server.gems.Gems.Cwfs3DetectorState
import observe.server.gems.Gems.GemsWfsState
import observe.server.gems.Gems.Odgw1DetectorState
import observe.server.gems.Gems.Odgw2DetectorState
import observe.server.gems.Gems.Odgw3DetectorState
import observe.server.gems.Gems.Odgw4DetectorState
import observe.server.gems.GemsController.GemsConfig
import observe.server.tcs.Gaos.PauseConditionSet
import observe.server.tcs.Gaos.PauseResume
import observe.server.tcs.Gaos.ResumeConditionSet

object GemsControllerSim {
  def apply[F[_]: Applicative](using L: Logger[F]): GemsController[F] =
    new GemsController[F] {
      override def pauseResume(pauseReasons: PauseConditionSet, resumeReasons: ResumeConditionSet)(
        cfg:                                 GemsConfig
      ): F[PauseResume[F]] =
        PauseResume(
          L.info(s"Simulate pausing GeMS loops because of $pauseReasons").some,
          L.info(s"Simulate restoring GeMS configuration $cfg because of $resumeReasons").some
        ).pure[F]

      override val stateGetter: Gems.GemsWfsState[F] = GemsWfsState[F](
        (Cwfs1DetectorState.Off: Cwfs1DetectorState).pure[F],
        (Cwfs2DetectorState.Off: Cwfs2DetectorState).pure[F],
        (Cwfs3DetectorState.Off: Cwfs3DetectorState).pure[F],
        (Odgw1DetectorState.Off: Odgw1DetectorState).pure[F],
        (Odgw2DetectorState.Off: Odgw2DetectorState).pure[F],
        (Odgw3DetectorState.Off: Odgw3DetectorState).pure[F],
        (Odgw4DetectorState.Off: Odgw4DetectorState).pure[F]
      )
    }
}
