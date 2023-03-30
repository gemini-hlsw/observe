// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

sealed abstract class ObserveCommandResult(val tag: String) extends Product with Serializable

object ObserveCommandResult {
  case object Success extends ObserveCommandResult("Success")
  case object Paused  extends ObserveCommandResult("Paused")
  case object Stopped extends ObserveCommandResult("Stopped")
  case object Aborted extends ObserveCommandResult("Aborted")

  /** @group Typeclass Instances */
  implicit val ObserveCommandResultEnumerated: Enumerated[ObserveCommandResult] =
    Enumerated.from(Success, Paused, Stopped, Aborted).withTag(_.tag)
}
