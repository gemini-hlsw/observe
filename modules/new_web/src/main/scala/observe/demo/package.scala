// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.demo

import cats.syntax.all.*
import observe.model.*
import lucuma.core.model.Observation
import lucuma.core.enums.Instrument

val demoSessionQueue: List[SessionQueueRow] =
  List(
    SessionQueueRow(
      Observation.Id.fromLong(27).get,
      SequenceState.Running(false, false),
      Instrument.GmosSouth,
      "Untitled".some,
      //  observer: Option[Observer],
      "GMOS-S Observation",
      ObsClass.Nighttime,
      true,
      true,
      none,
      //  runningStep: Option[RunningStep],
      false
    )
  )
