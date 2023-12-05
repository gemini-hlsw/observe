// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

enum ObserveCommandResult(val tag: String) derives Enumerated:
  case Success extends ObserveCommandResult("Success")
  case Paused  extends ObserveCommandResult("Paused")
  case Stopped extends ObserveCommandResult("Stopped")
  case Aborted extends ObserveCommandResult("Aborted")
