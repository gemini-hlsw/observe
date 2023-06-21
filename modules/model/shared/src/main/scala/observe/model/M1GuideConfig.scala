// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats._
import cats.syntax.all.*
import observe.model.enums.M1Source

/** Data type for M1 guide config. */
sealed trait M1GuideConfig extends Product with Serializable {
  def uses(s: M1Source): Boolean
}

object M1GuideConfig {
  case object M1GuideOff                       extends M1GuideConfig {
    override def uses(s: M1Source): Boolean = false
  }
  final case class M1GuideOn(source: M1Source) extends M1GuideConfig {
    override def uses(s: M1Source): Boolean = source === s
  }

  object M1GuideOn {
    given Show[M1GuideOn] = Show.fromToString
    given Eq[M1GuideOn]   = Eq.by(_.source)
  }

  given Show[M1GuideConfig] = Show.fromToString
  given Eq[M1GuideConfig]   = Eq.instance {
    case (M1GuideOff, M1GuideOff)     => true
    case (M1GuideOn(a), M1GuideOn(b)) => a === b
    case _                            => false
  }

}
