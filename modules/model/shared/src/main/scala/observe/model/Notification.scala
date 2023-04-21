// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.syntax.all.*
import observe.model.Observation
import observe.model.enums.Instrument
import observe.model.enums.Resource

sealed trait Notification extends Product with Serializable

object Notification {
  given Eq[Notification] =
    Eq.instance {
      case (a: ResourceConflict, b: ResourceConflict) => a === b
      case (a: InstrumentInUse, b: InstrumentInUse)   => a === b
      case (a: RequestFailed, b: RequestFailed)       => a === b
      case (a: SubsystemBusy, b: SubsystemBusy)       => a === b
      case _                                          => false
    }

  // Notification that user tried to run a sequence that used resource already in use
  final case class ResourceConflict(sidName: Observation.IdName) extends Notification
  object ResourceConflict {
    given Eq[ResourceConflict] =
      Eq.by(_.sidName)
  }

  // Notification that user tried to select a sequence for an instrument for which a sequence was already running
  final case class InstrumentInUse(sidName: Observation.IdName, ins: Instrument)
      extends Notification

  object InstrumentInUse {
    given Eq[InstrumentInUse] =
      Eq.by(x => (x.sidName, x.ins))
  }

  // Notification that a request to the backend failed
  final case class RequestFailed(msgs: List[String]) extends Notification

  object RequestFailed {
    given Eq[RequestFailed] =
      Eq.by(_.msgs)
  }

  // Notification that a resource configuration failed as the resource was busy
  final case class SubsystemBusy(sidName: Observation.IdName, stepId: StepId, resource: Resource)
      extends Notification

  object SubsystemBusy {
    given Eq[SubsystemBusy] =
      Eq.by(x => (x.sidName, x.stepId, x.resource))
  }
}
