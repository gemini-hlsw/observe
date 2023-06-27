// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s

import cats.effect.Sync
import observe.model.config.*

// This makes it cleaner that we have started SmartGcal
// Though in practice it can be bypassed with the Java API
sealed trait SmartGcal extends Product with Serializable

object SmartGcalInitializer {
  private final case class SmartGcalImpl() extends SmartGcal

  def init[F[_]: Sync](conf: SmartGcalConfiguration): F[SmartGcal] =
    Sync[F].delay {
      SmartGcalImpl()
    }
}
