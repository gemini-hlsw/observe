// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.all.*
import lucuma.core.util.Enumerated

sealed abstract class WaterVapor(val tag: String, val toInt: Option[Int], val label: String)
    extends Product
    with Serializable

object WaterVapor {

  case object Unknown   extends WaterVapor("Unknown", none, "Unknown")
  case object Percent20 extends WaterVapor("Percent20", 20.some, "20%/Low")
  case object Percent50 extends WaterVapor("Percent50", 50.some, "50%/Median")
  case object Percent80 extends WaterVapor("Percent80", 80.some, "85%/High")
  case object Any       extends WaterVapor("Any", 100.some, "Any")

  /** @group Typeclass Instances */
  given Enumerated[WaterVapor] =
    Enumerated.from(Unknown, Percent20, Percent50, Percent80, Any).withTag(_.tag)

}
