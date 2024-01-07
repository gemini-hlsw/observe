// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.config

import lucuma.core.util.Enumerated

/**
 * Operating mode
 */
enum Mode(val tag: String) derives Enumerated:
  case Production  extends Mode("Production")
  case Staging     extends Mode("Staging")
  case Development extends Mode("Development")
