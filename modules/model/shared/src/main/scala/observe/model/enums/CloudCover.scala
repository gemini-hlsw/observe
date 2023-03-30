// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.all._
import lucuma.core.util.Enumerated

sealed abstract class CloudCover(val tag: String, val toInt: Option[Int], val label: String)
    extends Product
    with Serializable

object CloudCover {

  case object Unknown   extends CloudCover("Unknown", none, "Unknown")
  case object Percent50 extends CloudCover("Percent50", 50.some, "50%/Clear")
  case object Percent70 extends CloudCover("Percent70", 70.some, "70%/Cirrus")
  case object Percent80 extends CloudCover("Percent80", 80.some, "80%/Cloudy")
  case object Any       extends CloudCover("Any", 100.some, "Any")

  /** @group Typeclass Instances */
  implicit val CloudCoverEnumerated: Enumerated[CloudCover] =
    Enumerated.from(Unknown, Percent50, Percent70, Percent80, Any).withTag(_.tag)
}
