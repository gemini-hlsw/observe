// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.derived.*
import cats.kernel.Order
import cats.syntax.all.*
import lucuma.core.enums.Instrument
import observe.model.enums.Resource

enum ActionType derives Order:
  case Observe
  case Undefined // Used in tests
  case Configure(sys: Resource | Instrument)
  case OdbEvent
