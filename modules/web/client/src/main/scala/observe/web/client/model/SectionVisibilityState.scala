// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import lucuma.core.util.Enumerated

// UI model
sealed abstract class SectionVisibilityState(val tag: String) extends Product with Serializable {
  def flip: SectionVisibilityState = this match {
    case SectionVisibilityState.SectionOpen   => SectionVisibilityState.SectionClosed
    case SectionVisibilityState.SectionClosed => SectionVisibilityState.SectionOpen
  }
}

object SectionVisibilityState {
  case object SectionOpen   extends SectionVisibilityState("SectionOpen")
  case object SectionClosed extends SectionVisibilityState("SectionClosed")

  /** @group Typeclass Instances */
  given Enumerated[SectionVisibilityState] =
    Enumerated.from(SectionOpen, SectionClosed).withTag(_.tag)

}
