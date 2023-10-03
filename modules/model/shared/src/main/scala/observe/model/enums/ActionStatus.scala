// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

enum ActionStatus(val tag: String) derives Enumerated:
  /** Action is not yet run. */
  case Pending extends ActionStatus("Pending")

  /** Action run and completed. */
  case Completed extends ActionStatus("Completed")

  /** Action currently running. */
  case Running extends ActionStatus("Running")

  /** Action run but paused. */
  case Paused extends ActionStatus("Paused")

  /** Action run but failed to complete. */
  case Failed extends ActionStatus("Failed")

  /** Action was aborted by the user */
  case Aborted extends ActionStatus("Aborted")
