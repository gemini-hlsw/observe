// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import cats.derived.*
import lucuma.core.model.Observation

// TODO Eventually, we will have parameters for sharable URLs
enum Page derives Eq:
  // case Schedule, Nighttime, Daytime, Excluded
  case Home                       extends Page
  case Obs(obsId: Observation.Id) extends Page
