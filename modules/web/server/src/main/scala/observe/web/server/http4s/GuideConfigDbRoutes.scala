// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.effect.Concurrent
import cats.syntax.all.*
import fs2.compression.Compression
import observe.server.tcs.GuideConfig
import observe.server.tcs.GuideConfigDb
import observe.server.tcs.GuideConfigDb.given
import org.http4s.EntityDecoder
import org.http4s.HttpRoutes
import org.http4s.circe.jsonOf
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.GZip
import org.typelevel.log4cats.Logger

class GuideConfigDbRoutes[F[_]: Concurrent: Compression: Logger](db: GuideConfigDb[F])
    extends Http4sDsl[F] {

  given EntityDecoder[F, GuideConfig] = jsonOf

  val publicService: HttpRoutes[F] = GZip {
    HttpRoutes.of { case req @ POST -> Root =>
      req.decode[GuideConfig] { guideConfig =>
        db.set(guideConfig) *>
          Logger[F].info(s"Received guide configuration $guideConfig") *>
          Ok("")
      }
    }
  }

  def service: HttpRoutes[F] = publicService

}
