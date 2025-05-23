// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.altair

import cats.Applicative
import cats.effect.Sync
import cats.syntax.all.*
import observe.common.ObsQueriesGQL.RecordDatasetMutation.Data.RecordDataset.Dataset
import observe.model.Observation.Id
import observe.model.dhs.ImageFileId
import observe.model.enums.KeywordName
import observe.server.keywords.*
import observe.server.tcs.CRFollow
import observe.server.tcs.TcsKeywordsReader
import org.typelevel.log4cats.Logger

object AltairHeader {
  def header[F[_]: Sync: Logger](
    kwClient:          KeywordsClient[F],
    altairReader:      AltairKeywordReader[F],
    tcsKeywordsReader: TcsKeywordsReader[F]
  ): Header[F] =
    new Header[F] {
      override def sendBefore(
        obsId:   Id,
        id:      ImageFileId,
        dataset: Option[Dataset.Reference]
      ): F[Unit] =
        sendKeywords(
          id,
          kwClient,
          List(
            buildDouble(altairReader.aofreq, KeywordName.AOFREQ),
            buildDouble(altairReader.aocounts, KeywordName.AOCOUNTS),
            buildDouble(altairReader.aoseeing, KeywordName.AOSEEING),
            buildDouble(altairReader.aowfsx, KeywordName.AOWFSX),
            buildDouble(altairReader.aowfsy, KeywordName.AOWFSY),
            buildDouble(altairReader.aowfsz, KeywordName.AOWFSZ),
            buildDouble(altairReader.aogain, KeywordName.AOGAIN),
            buildString(altairReader.aoncpa, KeywordName.AONCPAF),
            buildString(
              tcsKeywordsReader.crFollow.map(_.map(CRFollow.keywordValue).getOrElse("INDEF")),
              KeywordName.CRFOLLOW
            ),
            buildString(altairReader.ngndfilt, KeywordName.AONDFILT),
            buildString(altairReader.astar, KeywordName.AOFLENS),
            buildString(altairReader.aoflex, KeywordName.AOFLEXF),
            buildString(altairReader.lgustage, KeywordName.LGUSTAGE),
            buildString(altairReader.aobs, KeywordName.AOBS)
          )
        )

      override def sendAfter(id: ImageFileId): F[Unit] = Applicative[F].unit
    }

}
