// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.config

import cats.Eq
import lucuma.core.enum.Site

/**
 * Top configuration of the observe
 * @param site Site this observe instance handles (GN/GS)
 * @param mode Execution mode
 * @param observeEngine Configuration of the engine
 * @param webServer Web side configuration
 * @param smartGcal Configuration to reach SmartGCal
 * @param authentication Configuration to support authentication
 */
final case class ObserveConfiguration(
  site:           Site,
  mode:           Mode,
  observeEngine:  ObserveEngineConfiguration,
  webServer:      WebServerConfiguration,
  smartGcal:      SmartGcalConfiguration,
  authentication: AuthenticationConfig
)

object ObserveConfiguration {
  implicit val eqObserveConfiguration: Eq[ObserveConfiguration] =
    Eq.by(x => (x.site, x.mode, x.observeEngine, x.webServer, x.smartGcal, x.authentication))
}
