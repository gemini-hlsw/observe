// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

/** Enumerated type for Coma option. */
sealed abstract class ComaOption(val tag: String) extends Product with Serializable

object ComaOption {
  case object ComaOn  extends ComaOption("ComaOn")
  case object ComaOff extends ComaOption("ComaOff")

  /** @group Typeclass Instances */
  implicit val CommaOptionEnumerated: Enumerated[ComaOption] =
    Enumerated.from(ComaOn, ComaOff).withTag(_.tag)
}
