// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gws

import cats.*
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import observe.model.enums.KeywordName
import observe.model.Observation
import observe.model.dhs.ImageFileId
import observe.server.EpicsHealth
import observe.server.keywords.*

object GwsHeader {
  def header[F[_]: MonadThrow: Logger](
    kwClient:  KeywordsClient[F],
    gwsReader: GwsKeywordReader[F]
  ): Header[F] = new Header[F] {
    override def sendBefore(obsId: Observation.Id, id: ImageFileId): F[Unit] =
      gwsReader.health
        .map(_ === EpicsHealth.Good)
        .handleError(_ => false) // error check the health read
        .ifM(
          sendKeywords[F](
            id,
            kwClient,
            List(
              buildDouble(gwsReader.humidity, KeywordName.HUMIDITY),
              buildDouble(gwsReader.temperature.map(_.toCelsiusScale), KeywordName.TAMBIENT),
              buildDouble(gwsReader.temperature.map(_.toFahrenheitScale), KeywordName.TAMBIEN2),
              buildDouble(gwsReader.airPressure.map(_.toMillimetersOfMercury),
                          KeywordName.PRESSURE
              ),
              buildDouble(gwsReader.airPressure.map(_.toPascals), KeywordName.PRESSUR2),
              buildDouble(gwsReader.dewPoint.map(_.toCelsiusScale), KeywordName.DEWPOINT),
              buildDouble(gwsReader.dewPoint.map(_.toFahrenheitScale), KeywordName.DEWPOIN2),
              buildDouble(gwsReader.windVelocity.map(_.toMetersPerSecond), KeywordName.WINDSPEE),
              buildDouble(gwsReader.windVelocity.map(_.toInternationalMilesPerHour),
                          KeywordName.WINDSPE2
              ),
              buildDouble(gwsReader.windDirection.map(_.toDegrees), KeywordName.WINDDIRE)
            )
          ),
          Applicative[F].unit
        )

    override def sendAfter(id: ImageFileId): F[Unit] = Applicative[F].unit
  }
}
