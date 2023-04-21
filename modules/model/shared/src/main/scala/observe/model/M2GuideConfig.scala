// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats._
import cats.syntax.all.*
import observe.model.enums.ComaOption
import observe.model.enums.TipTiltSource

/** Data type for M2 guide config. */
sealed trait M2GuideConfig extends Product with Serializable {
  def uses(s: TipTiltSource): Boolean
}

object M2GuideConfig {
  case object M2GuideOff                                                    extends M2GuideConfig {
    override def uses(s: TipTiltSource): Boolean = false
  }
  final case class M2GuideOn(coma: ComaOption, sources: Set[TipTiltSource]) extends M2GuideConfig {
    override def uses(s: TipTiltSource): Boolean = sources.contains(s)
  }

  object M2GuideOn {
    given Eq[M2GuideOn] = Eq.by(x => (x.coma, x.sources))
  }

  given Show[M2GuideConfig] = Show.fromToString
  given Eq[M2GuideConfig]   = Eq.instance {
    case (M2GuideOff, M2GuideOff)                   => true
    case (a @ M2GuideOn(_, _), b @ M2GuideOn(_, _)) => a === b
    case _                                          => false
  }
}
