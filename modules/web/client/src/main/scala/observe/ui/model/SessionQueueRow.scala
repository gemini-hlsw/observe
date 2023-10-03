// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import cats.derived.*
import japgolly.scalajs.react.ReactCats.*
import japgolly.scalajs.react.Reusability
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import observe.model.Observer
import observe.model.RunningStep
import observe.model.SequenceState
import observe.ui.model.enums.ObsClass

case class SessionQueueRow(
  obsId:         Observation.Id,
  status:        SequenceState,
  instrument:    Instrument,
  targetName:    Option[String],
  observer:      Option[Observer],
  name:          String,
  obsClass:      ObsClass,
  // active:        Boolean,
  loaded:        Boolean,
  nextStepToRun: Option[Step.Id],
  runningStep:   Option[RunningStep],
  inDayCalQueue: Boolean
) derives Eq

object SessionQueueRow:
  given Reusability[SessionQueueRow] = Reusability.byEq
