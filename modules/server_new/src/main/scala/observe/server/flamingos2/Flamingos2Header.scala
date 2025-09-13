// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.flamingos2

import cats.Applicative
import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.Flamingos2ReadMode
import lucuma.core.model.sequence.flamingos2.Flamingos2DynamicConfig
import lucuma.core.model.sequence.flamingos2.Flamingos2StaticConfig
import observe.common.EventsGQL.RecordDatasetMutation.Data.RecordDataset.Dataset
import observe.model.Observation
import observe.model.dhs.ImageFileId
import observe.model.enums.KeywordName
import observe.server.keywords.*
import observe.server.tcs.TcsKeywordsReader
import org.typelevel.log4cats.Logger

object Flamingos2Header {
  def header[F[_]: Sync: Logger](
    kwClient:          KeywordsClient[F],
    f2ObsReader:       Flamingos2Header.ObsKeywordsReader[F],
    tcsKeywordsReader: TcsKeywordsReader[F]
  ): Header[F] =
    new Header[F] {
      override def sendBefore(
        obsId:   Observation.Id,
        id:      ImageFileId,
        dataset: Option[Dataset.Reference]
      ): F[Unit] =
        sendKeywords(
          id,
          kwClient,
          List(
            buildBoolean(
              f2ObsReader.preimage,
              KeywordName.PREIMAGE,
              DefaultHeaderValue.FalseDefaultValue
            ),
            buildString(tcsKeywordsReader.date, KeywordName.DATE_OBS),
            buildString(tcsKeywordsReader.ut, KeywordName.TIME_OBS),
            buildString(f2ObsReader.readMode, KeywordName.READMODE),
            buildInt32(f2ObsReader.nReads, KeywordName.NREADS)
          )
        )

      override def sendAfter(id: ImageFileId): F[Unit] = Applicative[F].unit
    }

  trait ObsKeywordsReader[F[_]] {
    def preimage: F[Boolean]
    def readMode: F[String]
    def nReads: F[Int]
  }

  object ObsKeywordsReader {
    def apply[F[_]: Applicative](
      staticConfig:  Flamingos2StaticConfig,
      dynamicConfig: Flamingos2DynamicConfig
    ): ObsKeywordsReader[F] =
      new ObsKeywordsReader[F] {
        override def preimage: F[Boolean] = staticConfig.mosPreImaging.toBoolean.pure[F]
        override def readMode: F[String]  =
          dynamicConfig.readMode match
            case Flamingos2ReadMode.Bright => "Bright".pure[F]
            case Flamingos2ReadMode.Medium => "Medium".pure[F]
            case Flamingos2ReadMode.Faint  => "Dark".pure[F]

        override def nReads: F[Int] = dynamicConfig.reads.reads.pure[F]
      }
  }

  trait InstKeywordsReader[F[_]] {
    def getHealth: F[String]
    def getState: F[String]
  }

  object InstKeywordReaderDummy {
    def apply[F[_]: Applicative]: InstKeywordsReader[F] =
      new InstKeywordsReader[F] {
        override def getHealth: F[String] = "GOOD".pure[F]

        override def getState: F[String] = "RUNNING".pure[F]
      }
  }

  object InstKeywordReaderEpics {
    def apply[F[_]](sys: Flamingos2Epics[F]): InstKeywordsReader[F] =
      new InstKeywordsReader[F] {
        override def getHealth: F[String] = sys.health

        override def getState: F[String] = sys.state
      }
  }

}
