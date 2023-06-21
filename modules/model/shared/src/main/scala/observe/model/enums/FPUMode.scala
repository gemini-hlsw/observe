// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.all.*
import lucuma.core.util.Enumerated

sealed abstract class FPUMode(val tag: String, val label: String) extends Product with Serializable

object FPUMode {

  case object BuiltIn extends FPUMode("BuiltIn", "BUILTIN")
  case object Custom  extends FPUMode("Custom", "CUSTOM_MASK")

  def fromString(s: String): Option[FPUMode] =
    Enumerated[FPUMode].all.find(_.label === s)

  /** @group Typeclass Instances */
  given Enumerated[FPUMode] =
    Enumerated.from(BuiltIn, Custom).withTag(_.tag)
}
