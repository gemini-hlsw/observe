// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.altair

import cats.Applicative
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import observe.server.overrideLogMessage
import observe.server.altair.AltairController.FieldLens
import observe.server.tcs.Gaos
import observe.server.tcs.Gaos.PauseResume
import squants.Time

class AltairControllerDisabled[F[_]: Logger: Applicative] extends AltairController[F] {
  override def pauseResume(
    pauseReasons:  Gaos.PauseConditionSet,
    resumeReasons: Gaos.ResumeConditionSet,
    fieldLens:     FieldLens
  )(cfg:           AltairController.AltairConfig): F[Gaos.PauseResume[F]] =
    PauseResume(
      overrideLogMessage("Altair", "pause AO loops").some,
      overrideLogMessage("Altair", "resume AO loops").some
    ).pure[F]

  override def observe(expTime: Time)(cfg: AltairController.AltairConfig): F[Unit] =
    overrideLogMessage("Altair", "observe")

  override def endObserve(cfg: AltairController.AltairConfig): F[Unit] =
    overrideLogMessage("Altair", "endObserve")

  override def isFollowing: F[Boolean] = false.pure[F]
}
