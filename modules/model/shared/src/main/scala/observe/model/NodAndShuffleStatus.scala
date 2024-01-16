// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.*
import cats.derived.*
import io.circe.*
import lucuma.core.util.TimeSpan
import lucuma.odb.json.time.transport.given
import monocle.Focus
import monocle.Lens
import observe.model.GmosParameters.*
import observe.model.enums.*

case class NsRunningState(action: NsAction, sub: NsSubexposure)
    derives Eq,
      Encoder.AsObject,
      Decoder

case class NodAndShuffleStatus(
  observing:         ActionStatus,
  totalExposureTime: TimeSpan,
  nodExposureTime:   TimeSpan,
  cycles:            NsCycles,
  state:             Option[NsRunningState]
) derives Eq,
      Encoder.AsObject,
      Decoder

object NodAndShuffleStatus:
  val observing: Lens[NodAndShuffleStatus, ActionStatus]       = Focus[NodAndShuffleStatus](_.observing)
  val totalExposureTime: Lens[NodAndShuffleStatus, TimeSpan]   =
    Focus[NodAndShuffleStatus](_.totalExposureTime)
  val nodExposureTime: Lens[NodAndShuffleStatus, TimeSpan]     =
    Focus[NodAndShuffleStatus](_.nodExposureTime)
  val cycles: Lens[NodAndShuffleStatus, NsCycles]              = Focus[NodAndShuffleStatus](_.cycles)
  val state: Lens[NodAndShuffleStatus, Option[NsRunningState]] = Focus[NodAndShuffleStatus](_.state)
