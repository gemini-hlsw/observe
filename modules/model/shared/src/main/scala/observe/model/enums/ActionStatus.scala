// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

sealed abstract class ActionStatus(val tag: String) extends Product with Serializable

object ActionStatus {

  /** Action is not yet run. */
  case object Pending extends ActionStatus("Pending")

  /** Action run and completed. */
  case object Completed extends ActionStatus("Completed")

  /** Action currently running. */
  case object Running extends ActionStatus("Running")

  /** Action run but paused. */
  case object Paused extends ActionStatus("Paused")

  /** Action run but failed to complete. */
  case object Failed extends ActionStatus("Failed")

  /** Action was aborted by the user */
  case object Aborted extends ActionStatus("Aborted")

  /** @group Typeclass Instances */
  given Enumerated[ActionStatus] =
    Enumerated.from(Pending, Completed, Running, Paused, Failed, Aborted).withTag(_.tag)
}
