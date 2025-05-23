// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.enums

import japgolly.scalajs.react.Reusability

enum SyncStatus:
  case Synced, OutOfSync

object SyncStatus:
  given Reusability[SyncStatus] = Reusability.by_==
