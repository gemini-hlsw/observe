// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

enum ObservationStage(val tag: String) derives Enumerated:
  case Idle       extends ObservationStage("Idle")
  case Preparing  extends ObservationStage("Preparing")
  case Acquiring  extends ObservationStage("Acquiring")
  case ReadingOut extends ObservationStage("ReadingOut")

object ObservationStage:
  def fromBooleans(prep: Boolean, acq: Boolean, rdout: Boolean): ObservationStage =
    if (prep) Preparing
    else if (acq) Acquiring
    else if (rdout) ReadingOut
    else Idle
