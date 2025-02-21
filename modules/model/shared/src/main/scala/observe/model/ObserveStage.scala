// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import lucuma.core.util.Enumerated

enum ObserveStage(val tag: String) derives Enumerated:
  case Idle       extends ObserveStage("Idle")
  case Preparing  extends ObserveStage("Preparing")
  case Exposure   extends ObserveStage("Exposure")
  case ReadingOut extends ObserveStage("ReadingOut")

object ObserveStage:
  def fromBooleans(prep: Boolean, exp: Boolean, rdout: Boolean): ObserveStage =
    if (prep) Preparing
    else if (exp) Exposure
    else if (rdout) ReadingOut
    else Idle
