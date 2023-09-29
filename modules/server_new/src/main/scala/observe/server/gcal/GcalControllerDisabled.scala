// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gcal

import observe.server.overrideLogMessage
import org.typelevel.log4cats.Logger

class GcalControllerDisabled[F[_]: Logger] extends GcalController[F] {
  override def applyConfig(config: GcalController.GcalConfig): F[Unit] =
    overrideLogMessage("GCAL", "applyConfig")
}
