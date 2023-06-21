// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import lucuma.core.util.Enumerated

sealed abstract class NodAndShuffleState(val tag: String) extends Product with Serializable

// We need to tell Gmos if we are N&S
object NodAndShuffleState {
  // Names taken from the old observe
  case object NodShuffle extends NodAndShuffleState("NodShuffle")
  case object Classic    extends NodAndShuffleState("Classic")

  /** @group Typeclass Instances */
  given Enumerated[NodAndShuffleState] =
    Enumerated.from(NodShuffle, Classic).withTag(_.tag)

}
