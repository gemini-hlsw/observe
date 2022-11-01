// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.Eq
import cats.derived.*

enum NsAction derives Eq:
  case Start, NodStart, NodComplete, StageObserveStart, StageObserveComplete, Done
