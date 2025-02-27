// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

enum RunOverride(val tag: String) derives Enumerated {

  /** Default case, do regular checks */
  case Default extends RunOverride("Default")

  /** Override the checks and try to run anyway */
  case Override extends RunOverride("Override")
}
