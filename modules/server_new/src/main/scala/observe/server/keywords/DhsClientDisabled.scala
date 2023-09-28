// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import cats.FlatMap
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import observe.model.dhs.ImageFileId
import observe.server.overrideLogMessage
import cats.effect.Clock

class DhsClientDisabled[F[_]: FlatMap: Clock: Logger] extends DhsClient[F] {

  val format: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

  override def createImage(p: DhsClient.ImageParameters): F[ImageFileId] = for {
    _    <- overrideLogMessage[F]("DHS", "setKeywords")
    date <- Clock[F].realTimeInstant.map(LocalDateTime.ofInstant(_, java.time.ZoneOffset.UTC))
  } yield ImageFileId(f"S${date.format(format)}S${date.getMinute() / (24 * 60 * 60)}%04d")

  override def setKeywords(id: ImageFileId, keywords: KeywordBag, finalFlag: Boolean): F[Unit] =
    overrideLogMessage("DHS", "setKeywords")
}
