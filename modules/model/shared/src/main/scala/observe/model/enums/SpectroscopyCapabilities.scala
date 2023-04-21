// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

/* TODO: Remove this file after updating dependency on lucuma-core > 0.9.0 */

import lucuma.core.util.Enumerated

sealed abstract class SpectroscopyCapabilities(val tag: String, val label: String)
    extends Product
    with Serializable

object SpectroscopyCapabilities {
  case object NodAndShuffle extends SpectroscopyCapabilities("NodAndShuffle", "Nod & Shuffle")
  case object Polarimetry   extends SpectroscopyCapabilities("Polarimetry", "Polarimetry")
  case object Corongraphy   extends SpectroscopyCapabilities("Corongraphy", "Corongraphy")

  given Enumerated[SpectroscopyCapabilities] =
    Enumerated.from(NodAndShuffle, Polarimetry, Corongraphy).withTag(_.tag)
}
