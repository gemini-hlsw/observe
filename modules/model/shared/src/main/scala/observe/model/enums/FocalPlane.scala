// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

/* TODO: Remove this file after updating dependency on lucuma-core > 0.9.0 */

import lucuma.core.util.Enumerated

enum FocalPlane(val tag: String, val label: String) derives Enumerated {
  case SingleSlit   extends FocalPlane("SingleSlit", label = "Single Slit")
  case MultipleSlit extends FocalPlane("MultipleSlit", label = "Multiple Slits")
  case IFU          extends FocalPlane("IFU", label = "IFU")
}
