// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model

import cats.Eq
import cats.derived.*
import lucuma.core.model.Observation
import lucuma.core.enums.Instrument
import observe.model.ExecutionStep
import lucuma.core.model.sequence.Step
import observe.model.RunningStep
import observe.model.enums.SequenceState

// Formerly StepsTableFocus
case class Execution(
  obsId:               Observation.Id,
  obsName:             String,
  instrument:          Instrument,
  sequenceState:       SequenceState,
  steps:               List[ExecutionStep],
  stepConfigDisplayed: Option[Step.Id],
  nextStepToRun:       Option[Step.Id],
  // selectedStep:        Option[Step.Id], // moved to state
  runningStep:         Option[RunningStep],
  isPreview:           Boolean,
  tabOperations:       TabOperations
) derives Eq
