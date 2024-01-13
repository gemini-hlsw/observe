// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

enum PendingObserveCmd(val tag: String) derives Enumerated:
  case PauseGracefully extends PendingObserveCmd("PauseGracefully")
  case StopGracefully  extends PendingObserveCmd("StopGracefully")
