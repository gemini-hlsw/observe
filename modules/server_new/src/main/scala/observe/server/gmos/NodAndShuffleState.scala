// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import lucuma.core.util.Enumerated

enum NodAndShuffleState(val tag: String) derives Enumerated {
  // Names taken from the old observe
  case NodShuffle extends NodAndShuffleState("NodShuffle")
  case Classic    extends NodAndShuffleState("Classic")
}
