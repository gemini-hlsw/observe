// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats._
import observe.model.GmosParameters.*
import observe.model.enums.*
import squants.Time

final case class NSRunningState(action: NSAction, sub: NSSubexposure)

object NSRunningState {
  given Eq[NSRunningState] =
    Eq.by(x => (x.action, x.sub))
}

final case class NodAndShuffleStatus(
  observing:         ActionStatus,
  totalExposureTime: Time,
  nodExposureTime:   Time,
  cycles:            NsCycles,
  state:             Option[NSRunningState]
)

object NodAndShuffleStatus {

  given Eq[NodAndShuffleStatus] =
    Eq.by(x => (x.observing, x.totalExposureTime, x.nodExposureTime, x.cycles, x.state))
}
