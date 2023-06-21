// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.all.*
import lucuma.core.util.Enumerated

sealed abstract class SkyBackground(val tag: String, val toInt: Option[Int], val label: String)
    extends Product
    with Serializable

object SkyBackground {

  case object Unknown   extends SkyBackground("Unknown", none, "Unknown")
  case object Percent20 extends SkyBackground("Percent20", 20.some, "20%/Darkest")
  case object Percent50 extends SkyBackground("Percent50", 50.some, "50%/Dark")
  case object Percent80 extends SkyBackground("Percent80", 80.some, "80%/Grey")
  case object Any       extends SkyBackground("Any", 100.some, "Any/Bright")

  /** @group Typeclass Instances */
  given Enumerated[SkyBackground] =
    Enumerated.from(Unknown, Percent20, Percent50, Percent80, Any).withTag(_.tag)
}
