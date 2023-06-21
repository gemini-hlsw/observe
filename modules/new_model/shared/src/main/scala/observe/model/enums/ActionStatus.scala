// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.Eq
import cats.derived.*

enum ActionStatus derives Eq:
  /** Action is not yet run. */
  case Pending extends ActionStatus

  /** Action run and completed. */
  case Completed extends ActionStatus

  /** Action currently running. */
  case Running extends ActionStatus

  /** Action run but paused. */
  case Paused extends ActionStatus

  /** Action run but failed to complete. */
  case Failed extends ActionStatus

  /** Action was aborted by the user */
  case Aborted extends ActionStatus
