// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import lucuma.core.math.Angle
import lucuma.core.math.Wavelength
import lucuma.core.model.TelescopeGuideConfig
import monocle.Focus
import monocle.Lens
import observe.server.tcs.TcsController.*

case class InstrumentPorts(
  flamingos2Port: Int,
  ghostPort:      Int,
  gmosPort:       Int,
  gnirsPort:      Int,
  gpiPort:        Int,
  gsaoiPort:      Int,
  nifsPort:       Int,
  niriPort:       Int
)

case class BaseEpicsTcsConfig(
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

object BaseEpicsTcsConfig {
  val iaa: Lens[BaseEpicsTcsConfig, Angle]                                 =
    Focus[BaseEpicsTcsConfig](_.iaa)
  val offset: Lens[BaseEpicsTcsConfig, FocalPlaneOffset]                   =
    Focus[BaseEpicsTcsConfig](_.offset)
  val wavelA: Lens[BaseEpicsTcsConfig, Wavelength]                         =
    Focus[BaseEpicsTcsConfig](_.wavelA)
  val pwfs1: Lens[BaseEpicsTcsConfig, GuiderConfig]                        =
    Focus[BaseEpicsTcsConfig](_.pwfs1)
  val pwfs2: Lens[BaseEpicsTcsConfig, GuiderConfig]                        =
    Focus[BaseEpicsTcsConfig](_.pwfs2)
  val oiwfs: Lens[BaseEpicsTcsConfig, GuiderConfig]                        =
    Focus[BaseEpicsTcsConfig](_.oiwfs)
  val telescopeGuideConfig: Lens[BaseEpicsTcsConfig, TelescopeGuideConfig] =
    Focus[BaseEpicsTcsConfig](_.telescopeGuideConfig)
  val scienceFoldPosition: Lens[BaseEpicsTcsConfig, Option[ScienceFold]]   =
    Focus[BaseEpicsTcsConfig](_.scienceFoldPosition)
  val hrwfsPickupPosition: Lens[BaseEpicsTcsConfig, HrwfsPickupPosition]   =
    Focus[BaseEpicsTcsConfig](_.hrwfsPickupPosition)
}
