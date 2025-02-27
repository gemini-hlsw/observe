// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

/* TODO: Remove this file after updating dependency on lucuma-core > 0.9.0 */

import lucuma.core.util.Enumerated

enum SpectroscopyCapabilities(val tag: String, val label: String) derives Enumerated {
  case NodAndShuffle extends SpectroscopyCapabilities("NodAndShuffle", "Nod & Shuffle")
  case Polarimetry   extends SpectroscopyCapabilities("Polarimetry", "Polarimetry")
  case Corongraphy   extends SpectroscopyCapabilities("Corongraphy", "Corongraphy")
}
