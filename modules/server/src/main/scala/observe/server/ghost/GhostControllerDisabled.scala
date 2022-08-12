// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.ghost

import cats.Applicative
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import observe.model.Observation
import observe.model.dhs.ImageFileId
import observe.server.overrideLogMessage
import observe.server.keywords.GdsClient
import observe.server.keywords.KeywordBag
import squants.time.Time

class GhostControllerDisabled[F[_]: Logger: Applicative] extends GhostController[F] {
  private val name = "GHOST"

  override def gdsClient: GdsClient[F] = new GdsClient[F] {
    override def setKeywords(id: ImageFileId, ks: KeywordBag): F[Unit] =
      overrideLogMessage(name, "setKeywords")

    override def openObservation(obsId: Observation.Id, id: ImageFileId, ks: KeywordBag): F[Unit] =
      overrideLogMessage(name, "openObservation")

    override def closeObservation(id: ImageFileId): F[Unit] =
      overrideLogMessage(name, "closeObservation")
  }

  override def applyConfig(config: GhostConfig): F[Unit] = overrideLogMessage(name, "applyConfig")

  override def observe(fileId: ImageFileId, expTime: Time): F[ImageFileId] =
    overrideLogMessage(name, s"observe $fileId").as(fileId)

  override def endObserve: F[Unit] = overrideLogMessage(name, "endObserve")
}
