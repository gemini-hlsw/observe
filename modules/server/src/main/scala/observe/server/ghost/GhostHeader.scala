// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.ghost

import cats.Applicative
import observe.model.Observation
import observe.model.dhs.ImageFileId
import observe.server.keywords._

object GhostHeader {

  def header[F[_]: Applicative]: Header[F] =
    new Header[F] {
      override def sendBefore(obsId: Observation.Id, id: ImageFileId): F[Unit] =
        Applicative[F].unit

      override def sendAfter(id: ImageFileId): F[Unit]                         =
        Applicative[F].unit
    }
}
