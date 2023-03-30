// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.syntax.all._
import lucuma.core.util.Enumerated

sealed abstract class EpicsHealth(val tag: String) extends Product with Serializable

object EpicsHealth {
  case object Good extends EpicsHealth("Good")
  case object Bad  extends EpicsHealth("Bad")
  implicit def fromInt(v: Int): EpicsHealth = if (v === 0) Good else Bad

  implicit val EpicsHealthEnumerated: Enumerated[EpicsHealth] =
    Enumerated.from(Good, Bad).withTag(_.tag)
}
