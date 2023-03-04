// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gcal

import cats.syntax.all._
import org.typelevel.log4cats.Logger
import observe.server.gcal.GcalController.GcalConfig
import observe.server.gcal.GcalController.gcalConfigShow

object GcalControllerSim {
  def apply[F[_]: Logger]: GcalController[F] = new GcalController[F] {
    override def applyConfig(config: GcalConfig): F[Unit] =
      Logger[F].debug(s"Simulating GCAL configuration: ${config.show}")
  }
}
