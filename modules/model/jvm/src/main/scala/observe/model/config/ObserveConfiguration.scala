// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.config

import cats.Eq
import cats.derived.*
import lucuma.core.enums.Site

/**
 * Top configuration of the observe
 * @param site
 *   Site this observe instance handles (GN/GS)
 * @param mode
 *   Execution mode
 * @param observeEngine
 *   Configuration of the engine
 * @param webServer
 *   Web side configuration
 * @param smartGcal
 *   Configuration to reach SmartGCal
 * @param authentication
 *   Configuration to support authentication
 */
case class ObserveConfiguration(
  site:           Site,
  mode:           Mode,
  lucumaSSO:      LucumaSSOConfiguration,
  observeEngine:  ObserveEngineConfiguration,
  webServer:      WebServerConfiguration,
  authentication: AuthenticationConfig
) derives Eq
