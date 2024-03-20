// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.altair

import cats.syntax.all.*
import lucuma.core.enums.Instrument
import lucuma.core.model.AltairConfig
import lucuma.core.util.TimeSpan
import observe.server.tcs.Gaos.GuideCapabilities
import observe.server.tcs.Gaos.PauseConditionSet
import observe.server.tcs.Gaos.ResumeConditionSet
import observe.server.tcs.TcsController.FocalPlaneOffset

trait AltairController[F[_]] {
  import AltairController._

  def pauseResume(
    pauseReasons:  PauseConditionSet,
    resumeReasons: ResumeConditionSet,
    currentOffset: FocalPlaneOffset,
    instrument:    Instrument
  )(cfg: AltairConfig): F[AltairPauseResume[F]]

  def observe(expTime: TimeSpan)(cfg: AltairConfig): F[Unit]

  def endObserve(cfg: AltairConfig): F[Unit]

  def isFollowing: F[Boolean]

}

object AltairController {

  sealed case class AltairPauseResume[F[_]](
    pause:             Option[F[Unit]],
    guideWhilePaused:  GuideCapabilities,
    pauseTargetFilter: Boolean,
    resume:            Option[F[Unit]],
    restoreOnResume:   GuideCapabilities,
    config:            Option[F[Unit]],
    forceFreeze:       Boolean
  )

}
