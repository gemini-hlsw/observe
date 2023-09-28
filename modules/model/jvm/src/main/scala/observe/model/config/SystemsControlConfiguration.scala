// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.config

import cats.Eq
import cats.derived.*

/**
 * Indicates how each subsystems is treated, e.g. full connection or simulated
 */
case class SystemsControlConfiguration(
  altair:   ControlStrategy,
  gems:     ControlStrategy,
  dhs:      ControlStrategy,
  f2:       ControlStrategy,
  gcal:     ControlStrategy,
  gmos:     ControlStrategy,
  gnirs:    ControlStrategy,
  gpi:      ControlStrategy,
  gpiGds:   ControlStrategy,
  ghost:    ControlStrategy,
  ghostGds: ControlStrategy,
  gsaoi:    ControlStrategy,
  gws:      ControlStrategy,
  nifs:     ControlStrategy,
  niri:     ControlStrategy,
  tcs:      ControlStrategy
) derives Eq {
  def connectEpics: Boolean =
    altair.connect || gems.connect || f2.connect || gcal.connect || gmos.connect || gnirs.connect || gsaoi.connect || gws.connect || nifs.connect || niri.connect || tcs.connect
}
