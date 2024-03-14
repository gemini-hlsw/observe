// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.altair

import cats.Applicative
import cats.syntax.all.*
import lucuma.core.enums.Instrument
import lucuma.core.util.TimeSpan
import observe.server.altair.AltairController.AltairPauseResume
import observe.server.tcs.Gaos.GuideCapabilities
import observe.server.tcs.Gaos.PauseConditionSet
import observe.server.tcs.Gaos.ResumeConditionSet
import observe.server.tcs.TcsController
import org.typelevel.log4cats.Logger

object AltairControllerSim {
  def apply[F[_]: Applicative: Logger]: AltairController[F] = new AltairController[F] {
    private val L = Logger[F]

    override def pauseResume(
      pauseReasons:  PauseConditionSet,
      resumeReasons: ResumeConditionSet,
      currentOffset: TcsController.FocalPlaneOffset,
      instrument:    Instrument
    )(cfg: AltairController.AltairConfig): F[AltairPauseResume[F]] =
      AltairPauseResume(
        L.info(s"Simulate pausing Altair loops because of $pauseReasons").some,
        GuideCapabilities(canGuideM2 = false, canGuideM1 = false),
        pauseTargetFilter = false,
        L.info(s"Simulate restoring Altair configuration $cfg because of $resumeReasons").some,
        GuideCapabilities(canGuideM2 = false, canGuideM1 = false),
        none,
        forceFreeze = true
      ).pure[F]

    override def observe(expTime: TimeSpan)(cfg: AltairController.AltairConfig): F[Unit] =
      L.info("Simulate observe notification for Altair")

    override def endObserve(cfg: AltairController.AltairConfig): F[Unit] =
      L.info("Simulate endObserve notification for Altair")

    override def isFollowing: F[Boolean] = false.pure[F]
  }
}
