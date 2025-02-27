// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

enum ApplyCommandResult(val tag: String) derives Enumerated {
  case Paused    extends ApplyCommandResult("Paused")
  case Completed extends ApplyCommandResult("Completed")
}
