// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*

case class SystemOverrides(
  isTcsEnabled:        SubsystemEnabled,
  isInstrumentEnabled: SubsystemEnabled,
  isGcalEnabled:       SubsystemEnabled,
  isDhsEnabled:        SubsystemEnabled
) derives Eq {
  def disableTcs: SystemOverrides = copy(isTcsEnabled = SubsystemEnabled.Disabled)

  def enableTcs: SystemOverrides = copy(isTcsEnabled = SubsystemEnabled.Enabled)

  def disableInstrument: SystemOverrides = copy(isInstrumentEnabled = SubsystemEnabled.Disabled)

  def enableInstrument: SystemOverrides = copy(isInstrumentEnabled = SubsystemEnabled.Enabled)

  def disableGcal: SystemOverrides = copy(isGcalEnabled = SubsystemEnabled.Disabled)

  def enableGcal: SystemOverrides = copy(isGcalEnabled = SubsystemEnabled.Enabled)

  def disableDhs: SystemOverrides = copy(isDhsEnabled = SubsystemEnabled.Disabled)

  def enableDhs: SystemOverrides = copy(isDhsEnabled = SubsystemEnabled.Enabled)
}

object SystemOverrides {
  val AllEnabled: SystemOverrides = SystemOverrides(
    isTcsEnabled = SubsystemEnabled.Enabled,
    isInstrumentEnabled = SubsystemEnabled.Enabled,
    isGcalEnabled = SubsystemEnabled.Enabled,
    isDhsEnabled = SubsystemEnabled.Enabled
  )

}
