// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import lucuma.core.util.Enumerated

sealed abstract class RunOverride(val tag: String) extends Product with Serializable

object RunOverride {

  /** Default case, do regular checks */
  case object Default extends RunOverride("Default")

  /** Override the checks and try to run anyway */
  case object Override extends RunOverride("Override")

  /** @group Typeclass Instances */
  given Enumerated[RunOverride] =
    Enumerated.from(Default, Override).withTag(_.tag)
}
