// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import observe.model.enums.Instrument
import observe.model.enums.Resource

enum Notification derives Eq:
  // Notification that user tried to run a sequence that used resource already in use
  case ResourceConflict(obsId: Observation.Id)                                  extends Notification
  // Notification that user tried to select a sequence for an instrument for which a sequence was already running
  case InstrumentInUse(obsId: Observation.Id, ins: Instrument)                  extends Notification
  // Notification that a request to the backend failed
  case RequestFailed(msgs: List[String])                                        extends Notification
  // Notification that a resource configuration failed as the resource was busy
  case SubsystemBusy(obsId: Observation.Id, stepId: StepId, resource: Resource) extends Notification
