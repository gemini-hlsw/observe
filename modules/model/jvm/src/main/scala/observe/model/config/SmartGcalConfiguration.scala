// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.config

import java.nio.file.Path

import cats.Eq
import org.http4s.Uri

/**
 * Configuration for Smart Gcal
 * @param smartGcalHost
 *   Host where smartgcal runs
 * @param smartGcalDir
 *   Local directory to store cached files
 */
final case class SmartGcalConfiguration(
  smartGcalHost: Uri,
  smartGcalDir:  Path
)

object SmartGcalConfiguration {
  given Eq[SmartGcalConfiguration] =
    Eq.by(x => (x.smartGcalHost, x.smartGcalDir))
}
