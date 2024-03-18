// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.altair

import cats.Applicative
import cats.syntax.all.*
import lucuma.core.enums.Instrument
import lucuma.core.model.AltairConfig
import lucuma.core.util.TimeSpan
import observe.server.altair.AltairController.AltairPauseResume
import observe.server.overrideLogMessage
import observe.server.tcs.Gaos
import observe.server.tcs.Gaos.GuideCapabilities
import observe.server.tcs.TcsController.FocalPlaneOffset
import org.typelevel.log4cats.Logger

class AltairControllerDisabled[F[_]: Logger: Applicative] extends AltairController[F] {
  override def pauseResume(
    pauseReasons:  Gaos.PauseConditionSet,
    resumeReasons: Gaos.ResumeConditionSet,
    currentOffset: FocalPlaneOffset,
    instrument:    Instrument
  )(cfg: AltairConfig): F[AltairPauseResume[F]] =
    AltairPauseResume(
      overrideLogMessage("Altair", "pause AO loops").some,
      GuideCapabilities(canGuideM2 = false, canGuideM1 = false),
      pauseTargetFilter = false,
      overrideLogMessage("Altair", "resume AO loops").some,
      GuideCapabilities(canGuideM2 = false, canGuideM1 = false),
      none,
      forceFreeze = true
    ).pure[F]

  override def observe(expTime: TimeSpan)(cfg: AltairConfig): F[Unit] =
    overrideLogMessage("Altair", "observe")

  override def endObserve(cfg: AltairConfig): F[Unit] =
    overrideLogMessage("Altair", "endObserve")

  override def isFollowing: F[Boolean] = false.pure[F]

}
