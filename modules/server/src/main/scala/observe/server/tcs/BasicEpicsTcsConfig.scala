// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import lucuma.core.math.Wavelength
import observe.model.TelescopeGuideConfig
import observe.server.tcs.TcsController.*
import squants.Angle

final case class InstrumentPorts(
  flamingos2Port: Int,
  ghostPort:      Int,
  gmosPort:       Int,
  gnirsPort:      Int,
  gpiPort:        Int,
  gsaoiPort:      Int,
  nifsPort:       Int,
  niriPort:       Int
)

final case class BaseEpicsTcsConfig(
  iaa:                  Angle,
  offset:               FocalPlaneOffset,
  wavelA:               Wavelength,
  pwfs1:                GuiderConfig,
  pwfs2:                GuiderConfig,
  oiwfs:                GuiderConfig,
  oiName:               String,
  telescopeGuideConfig: TelescopeGuideConfig,
  aoFold:               AoFold,
  useAo:                Boolean,
  scienceFoldPosition:  Option[ScienceFold],
  hrwfsPickupPosition:  HrwfsPickupPosition,
  instPorts:            InstrumentPorts
) {
  val instrumentOffset: InstrumentOffset = offset.toInstrumentOffset(iaa)
}
