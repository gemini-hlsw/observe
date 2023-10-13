// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.display

// import cats.syntax.eq.*
import lucuma.core.enums.Instrument
import lucuma.core.syntax.display.*
import lucuma.core.util.Display
import observe.model.RunningStep
import observe.model.enums.*

// private def conditionIntToString(v: Int): String = if (v === 100) "Any" else v.toString

// private def conditionDisplay[T](intValue: T => Option[Int], label: T => String): Display[T] =
//   Display.by(intValue(_).map(conditionIntToString).getOrElse("Unknown"), label)

// given Display[CloudExtinction] = conditionDisplay(_.toInt, _.label)
// given Display[ImageQuality]    = conditionDisplay(_.toInt, _.label)
// given Display[SkyBackground]   = conditionDisplay(_.toInt, _.label)
// given Display[WaterVapor]      = conditionDisplay(_.toInt, _.label)

given Display[Resource] = Display.byShortName(_.label)

given Display[Resource | Instrument] = Display.by(
  {
    case r: Resource   => r.shortName
    case i: Instrument => i.shortName
  },
  {
    case r: Resource   => r.longName
    case i: Instrument => i.longName
  }
)

given Display[RunningStep] = Display.byShortName(u => s"${u.last + 1}/${u.total}")
