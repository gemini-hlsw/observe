// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import io.circe.Decoder
import io.circe.Encoder
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.Step
import observe.model.enums.Resource

enum Notification derives Eq, Encoder, Decoder:
  // Notification that user tried to run a sequence that used resource already in use
  case ResourceConflict(obsId: Observation.Id)                  extends Notification
  // Notification that user tried to select a sequence for an instrument for which a sequence was already running
  case InstrumentInUse(obsId: Observation.Id, ins: Instrument)  extends Notification
  // Notification that a request to load a sequence in the backend failed
  case LoadingFailed(obsId: Observation.Id, msgs: List[String]) extends Notification
  // Notification that a resource configuration failed as the resource was busy
  case SubsystemBusy(obsId: Observation.Id, stepId: Step.Id, resource: Resource | Instrument)
      extends Notification
