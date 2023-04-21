// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.all.*
import lucuma.core.util.Enumerated

sealed abstract class Guiding(val tag: String, val configValue: String)
    extends Product
    with Serializable

object Guiding {

  case object Guide  extends Guiding("Guide", "guide")
  case object Park   extends Guiding("Park", "park")
  case object Freeze extends Guiding("Freeze", "freeze")

  def fromString(s: String): Option[Guiding] =
    Enumerated[Guiding].all.find(_.configValue === s)

  /** @group Typeclass Instances */
  given Enumerated[Guiding] =
    Enumerated.from(Guide, Park, Freeze).withTag(_.tag)

}
