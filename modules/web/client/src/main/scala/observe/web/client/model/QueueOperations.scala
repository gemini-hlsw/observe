// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import cats.Eq
import lucuma.core.util.Enumerated

sealed abstract class AddDayCalOperation(val tag: String) extends Product with Serializable
object AddDayCalOperation {
  case object AddDayCalIdle     extends AddDayCalOperation("AddDayCalIdle")
  case object AddDayCalInFlight extends AddDayCalOperation("AddDayCalInFlight")

  given Enumerated[AddDayCalOperation] =
    Enumerated.from(AddDayCalIdle, AddDayCalInFlight).withTag(_.tag)

}

sealed abstract class ClearAllCalOperation(val tag: String) extends Product with Serializable
object ClearAllCalOperation {
  case object ClearAllCalIdle     extends ClearAllCalOperation("ClearAllCalIdle")
  case object ClearAllCalInFlight extends ClearAllCalOperation("ClearAllCalInFlight")

  given Enumerated[ClearAllCalOperation] =
    Enumerated.from(ClearAllCalIdle, ClearAllCalInFlight).withTag(_.tag)

}

sealed abstract class RunCalOperation(val tag: String) extends Product with Serializable
object RunCalOperation {
  case object RunCalIdle     extends RunCalOperation("RunCalIdle")
  case object RunCalInFlight extends RunCalOperation("RunCalInFlight")

  given Enumerated[RunCalOperation] =
    Enumerated.from(RunCalIdle, RunCalInFlight).withTag(_.tag)

}

sealed abstract class StopCalOperation(val tag: String) extends Product with Serializable
object StopCalOperation {
  case object StopCalIdle     extends StopCalOperation("StopCalIdle")
  case object StopCalInFlight extends StopCalOperation("StopCalInFlight")

  given Enumerated[StopCalOperation] =
    Enumerated.from(StopCalIdle, StopCalInFlight).withTag(_.tag)

}

/**
 * Hold transient states while excuting an operation on the queue
 */
final case class QueueOperations(
  addDayCalRequested:   AddDayCalOperation,
  clearAllCalRequested: ClearAllCalOperation,
  runCalRequested:      RunCalOperation,
  stopCalRequested:     StopCalOperation
)

object QueueOperations {
  given Eq[QueueOperations] =
    Eq.by(x =>
      (x.addDayCalRequested, x.clearAllCalRequested, x.runCalRequested, x.stopCalRequested)
    )

  val Default: QueueOperations =
    QueueOperations(AddDayCalOperation.AddDayCalIdle,
                    ClearAllCalOperation.ClearAllCalIdle,
                    RunCalOperation.RunCalIdle,
                    StopCalOperation.StopCalIdle
    )
}
