// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import observe.model.dhs.ImageFileId
import observe.server.keywords.*

object FileIdProvider {

  def fileId[F[_]](env: ObserveEnvironment[F]): F[ImageFileId] =
    // All instruments ask the DHS for an ImageFileId
    env.dhs
      .dhsClient("")
      .createImage(
        DhsClient.ImageParameters(DhsClient.Permanent, List(env.inst.contributorName, "dhs-http"))
      )

}
