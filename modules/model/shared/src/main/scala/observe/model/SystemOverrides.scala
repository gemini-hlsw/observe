// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import io.circe.Decoder
import io.circe.Encoder
import monocle.Focus
import monocle.Lens

case class SystemOverrides(
  isTcsEnabled:        SubsystemEnabled,
  isInstrumentEnabled: SubsystemEnabled,
  isGcalEnabled:       SubsystemEnabled,
  isDhsEnabled:        SubsystemEnabled
) derives Eq,
      Encoder.AsObject,
      Decoder:
  def disableTcs: SystemOverrides = copy(isTcsEnabled = SubsystemEnabled.Disabled)

  def enableTcs: SystemOverrides = copy(isTcsEnabled = SubsystemEnabled.Enabled)

  def disableInstrument: SystemOverrides = copy(isInstrumentEnabled = SubsystemEnabled.Disabled)

  def enableInstrument: SystemOverrides = copy(isInstrumentEnabled = SubsystemEnabled.Enabled)

  def disableGcal: SystemOverrides = copy(isGcalEnabled = SubsystemEnabled.Disabled)

  def enableGcal: SystemOverrides = copy(isGcalEnabled = SubsystemEnabled.Enabled)

  def disableDhs: SystemOverrides = copy(isDhsEnabled = SubsystemEnabled.Disabled)

  def enableDhs: SystemOverrides = copy(isDhsEnabled = SubsystemEnabled.Enabled)

object SystemOverrides:
  val AllEnabled: SystemOverrides = SystemOverrides(
    isTcsEnabled = SubsystemEnabled.Enabled,
    isInstrumentEnabled = SubsystemEnabled.Enabled,
    isGcalEnabled = SubsystemEnabled.Enabled,
    isDhsEnabled = SubsystemEnabled.Enabled
  )

  val isTcsEnabled: Lens[SystemOverrides, SubsystemEnabled]        =
    Focus[SystemOverrides](_.isTcsEnabled)
  val isInstrumentEnabled: Lens[SystemOverrides, SubsystemEnabled] =
    Focus[SystemOverrides](_.isInstrumentEnabled)
  val isGcalEnabled: Lens[SystemOverrides, SubsystemEnabled]       =
    Focus[SystemOverrides](_.isGcalEnabled)
  val isDhsEnabled: Lens[SystemOverrides, SubsystemEnabled]        =
    Focus[SystemOverrides](_.isDhsEnabled)
