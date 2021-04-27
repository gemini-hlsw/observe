// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gcal

import org.typelevel.log4cats.Logger
import observe.server.overrideLogMessage

class GcalControllerDisabled[F[_]: Logger] extends GcalController[F] {
  override def applyConfig(config: GcalController.GcalConfig): F[Unit] =
    overrideLogMessage("GCAL", "applyConfig")
}
