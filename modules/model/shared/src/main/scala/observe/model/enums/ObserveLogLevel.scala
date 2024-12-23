// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

enum ObserveLogLevel(val tag: String, val label: String) derives Enumerated:
  case Info    extends ObserveLogLevel("INFO", "INFO")
  case Warning extends ObserveLogLevel("WARNING", "WARNING")
  case Error   extends ObserveLogLevel("ERROR", "ERROR")
