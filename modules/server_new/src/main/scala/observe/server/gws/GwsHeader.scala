// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gws

import cats.*
import cats.syntax.all.*
import coulomb.*
import coulomb.policy.standard.given
import coulomb.units.mks.Pascal
import coulomb.units.si.Second
import coulomb.units.temperature.Fahrenheit
import coulomb.units.us.Mile
import observe.common.ObsQueriesGQL.RecordDatasetMutation.Data.RecordDataset.Dataset
import observe.model.Observation
import observe.model.dhs.ImageFileId
import observe.model.enums.KeywordName
import observe.server.EpicsHealth
import observe.server.keywords.*
import org.typelevel.log4cats.Logger

object GwsHeader {
  def header[F[_]: MonadThrow: Logger](
    kwClient:  KeywordsClient[F],
    gwsReader: GwsKeywordReader[F]
  ): Header[F] = new Header[F] {
    override def sendBefore(
      obsId:   Observation.Id,
      id:      ImageFileId,
      dataset: Option[Dataset.Reference]
    ): F[Unit] =
      gwsReader.health
        .map(_ === EpicsHealth.Good)
        .handleError(_ => false) // error check the health read
        .ifM(
          sendKeywords[F](
            id,
            kwClient,
            List(
              buildDouble(gwsReader.humidity, KeywordName.HUMIDITY),
              buildDouble(gwsReader.temperature.map(_.value), KeywordName.TAMBIENT),
              buildDouble(gwsReader.temperature.map(_.toUnit[Fahrenheit].value),
                          KeywordName.TAMBIEN2
              ),
              buildDouble(gwsReader.airPressure.map(_.toUnit[MillimeterOfMercury].value),
                          KeywordName.PRESSURE
              ),
              buildDouble(gwsReader.airPressure.map(_.toUnit[Pascal].value), KeywordName.PRESSUR2),
              buildDouble(gwsReader.dewPoint.map(_.value.value), KeywordName.DEWPOINT),
              buildDouble(gwsReader.dewPoint.map(_.value.toUnit[Fahrenheit].value),
                          KeywordName.DEWPOIN2
              ),
              buildDouble(gwsReader.windVelocity.map(_.value), KeywordName.WINDSPEE),
              buildDouble(gwsReader.windVelocity.map(_.toUnit[Mile / Second].value),
                          KeywordName.WINDSPE2
              ),
              buildDouble(gwsReader.windDirection.map(_.toDoubleDegrees), KeywordName.WINDDIRE)
            )
          ),
          Applicative[F].unit
        )

    override def sendAfter(id: ImageFileId): F[Unit] = Applicative[F].unit
  }
}
