// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

/* TODO: Remove this file after updating dependency on lucuma-core > 0.9.0 */

import lucuma.core.util.Enumerated

// TODO Move to lucuma core
enum ScienceMode(val tag: String) derives Enumerated {
  case Imaging      extends ScienceMode("Imaging")
  case Spectroscopy extends ScienceMode("Spectroscopy")
}
