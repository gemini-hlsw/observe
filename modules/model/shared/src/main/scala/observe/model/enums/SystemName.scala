// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

enum SystemName(val tag: String, val system: String) derives Enumerated:
  case Ocs            extends SystemName("Ocs", "ocs")
  case Observe        extends SystemName("Observe", "observe")
  case Instrument     extends SystemName("Instrument", "instrument")
  case Telescope      extends SystemName("Telescope", "telescope")
  case Gcal           extends SystemName("Gcal", "gcal")
  case Calibration    extends SystemName("Calibration", "calibration")
  case Meta           extends SystemName("Meta", "meta")
  case AdaptiveOptics extends SystemName("AdaptiveOptics", "adaptive optics")
