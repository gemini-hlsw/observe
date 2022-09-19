// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.Eq
import cats.derived.*

enum ObservationStage derives Eq:
  case Idle, Preparing, Acquiring, ReadingOut

object ObservationStage:
  def fromBooleans(prep: Boolean, acq: Boolean, rdout: Boolean): ObservationStage =
    if (prep) Preparing
    else if (acq) Acquiring
    else if (rdout) ReadingOut
    else Idle
