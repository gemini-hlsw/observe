// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import lucuma.core.util.Enumerated

enum ObserveStage(val tag: String) derives Enumerated:
  case Idle       extends ObserveStage("Idle")
  case Preparing  extends ObserveStage("Preparing")
  case Acquiring  extends ObserveStage("Acquiring")
  case ReadingOut extends ObserveStage("ReadingOut")

object ObserveStage:
  def fromBooleans(prep: Boolean, acq: Boolean, rdout: Boolean): ObserveStage =
    if (prep) Preparing
    else if (acq) Acquiring
    else if (rdout) ReadingOut
    else Idle
