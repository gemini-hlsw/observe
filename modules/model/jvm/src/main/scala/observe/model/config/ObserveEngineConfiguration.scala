// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.config

import cats.Eq
import lucuma.core.util.NewType
import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

object GpiUriSettings extends NewType[Uri]
type GpiUriSettings = GpiUriSettings.Type

object GhostUriSettings extends NewType[Uri]
type GhostUriSettings = GhostUriSettings.Type

/**
 * Configuration of the Observe Engine
 * @param odb
 *   Location of the odb server
 * @param dhsServer
 *   Location of the dhs server proxy
 * @param systemControl
 *   Control of the subsystems
 * @param odbNotifications
 *   Indicates if we notify the odb of sequence events
 * @param instForceError
 *   Used for testing to simulate errors
 * @param failAt
 *   At what step to fail if we simulate errors
 * @param odbQueuePollingInterval
 *   frequency to check the odb queue
 * @param gpiUrl
 *   URL for the GPI GMP
 * @param gpiGds
 *   URL for GPI's GDS
 * @param ghostUrl
 *   URL for GHOST GMP
 * @param ghostGds
 *   URL for GHOST's GDS
 * @param tops
 *   Used to select the top component for epics subsystems
 * @param epicsCaAddrList
 *   List of IPs for the epics subsystem
 * @param readRetries
 *   Number of retries when reading a channel
 * @param ioTimeout
 *   Timeout to listen for EPICS events
 * @param dhsTimeout
 *   Timeout for DHS operations
 */
final case class ObserveEngineConfiguration(
  odb:                     Uri,
  dhsServer:               Uri,
  systemControl:           SystemsControlConfiguration,
  odbNotifications:        Boolean,
  instForceError:          Boolean,
  failAt:                  Int,
  odbQueuePollingInterval: FiniteDuration,
  gpiUrl:                  GpiUriSettings,
  gpiGDS:                  GpiUriSettings,
  ghostUrl:                GhostUriSettings,
  ghostGds:                GhostUriSettings,
  tops:                    String,
  epicsCaAddrList:         Option[String],
  readRetries:             Int,
  ioTimeout:               FiniteDuration,
  dhsTimeout:              FiniteDuration
)

object ObserveEngineConfiguration {

  given Eq[SystemsControlConfiguration] = Eq.instance { case (_, _) => true }
  given Eq[ObserveEngineConfiguration]  =
    Eq.by(x =>
      (x.odb,
       x.dhsServer,
       // x.systemControl,
       x.odbNotifications,
       x.instForceError,
       x.failAt,
       x.odbQueuePollingInterval,
       x.gpiUrl,
       x.gpiGDS,
       x.ghostUrl,
       x.ghostGds,
       x.tops,
       x.epicsCaAddrList,
       x.readRetries,
       x.ioTimeout,
       x.dhsTimeout
      )
    )

}
