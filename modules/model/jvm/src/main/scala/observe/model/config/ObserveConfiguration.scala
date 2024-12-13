// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.config

import cats.Eq
import cats.derived.*
import lucuma.core.enums.ExecutionEnvironment
import lucuma.core.enums.Site
import org.http4s.Uri

/**
 * Top configuration of the observe
 * @param site
 *   Site this observe instance handles (GN/GS)
 * @param environment
 *   Execution environment
 * @param exploreBaseUrl
 *   Location of explore
 * @param lucumaSSO
 *   Configuration of Single Sign On server
 * @param observeEngine
 *   Configuration of the engine
 * @param webServer
 *   Website configuration
 */
case class ObserveConfiguration(
  site:           Site,
  environment:    ExecutionEnvironment,
  exploreBaseUrl: Uri,
  lucumaSSO:      LucumaSSOConfiguration,
  observeEngine:  ObserveEngineConfiguration,
  webServer:      WebServerConfiguration
) derives Eq
