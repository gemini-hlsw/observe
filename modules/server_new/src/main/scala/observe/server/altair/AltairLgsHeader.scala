// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.altair

import cats.Applicative
import cats.effect.Sync
import observe.common.ObsQueriesGQL.RecordDatasetMutation.Data.RecordDataset.Dataset
import observe.model.Observation.Id
import observe.model.dhs.ImageFileId
import observe.model.enums.KeywordName
import observe.server.keywords.*
import org.typelevel.log4cats.Logger

object AltairLgsHeader {

  def header[F[_]: Sync: Logger](
    kwClient:     KeywordsClient[F],
    altairReader: AltairKeywordReader[F]
  ): Header[F] =
    new Header[F] {

      override def sendAfter(id: ImageFileId): F[Unit] =
        sendKeywords(
          id,
          kwClient,
          List(
            buildDouble(altairReader.lgdfocus, KeywordName.LGDFOCUS),
            buildDouble(altairReader.lgttcnts, KeywordName.LGTTCNTS),
            buildDouble(altairReader.lgttexp, KeywordName.LGTTEXP),
            buildDouble(altairReader.lgsfcnts, KeywordName.LGSFCNTS),
            buildDouble(altairReader.lgsfexp, KeywordName.LGSFEXP),
            buildDouble(altairReader.fsmtip, KeywordName.FSMTIP),
            buildDouble(altairReader.fsmtilt, KeywordName.FSMTILT),
            buildDouble(altairReader.lgzmpos, KeywordName.LGZMPOS),
            buildDouble(altairReader.naalt, KeywordName.NAALT),
            buildDouble(altairReader.nathick, KeywordName.NATHICK),
            buildString(altairReader.lgndfilt, KeywordName.LGNDFILT),
            buildString(altairReader.lgttiris, KeywordName.LGTTIRIS)
          )
        )

      override def sendBefore(
        obsId:   Id,
        id:      ImageFileId,
        dataset: Option[Dataset.Reference]
      ): F[Unit] =
        Applicative[F].unit
    }

}
