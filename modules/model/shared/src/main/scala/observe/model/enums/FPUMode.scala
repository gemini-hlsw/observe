// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.all.*
import lucuma.core.util.Enumerated

enum FPUMode(val tag: String, val label: String) derives Enumerated {
  case BuiltIn extends FPUMode("BuiltIn", "BUILTIN")
  case Custom  extends FPUMode("Custom", "CUSTOM_MASK")
}
object FPUMode                                                      {
  def fromString(s: String): Option[FPUMode] =
    Enumerated[FPUMode].all.find(_.label === s)
}
