// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.display

import cats.syntax.eq.*
import lucuma.core.util.Display
import observe.model.enums.*

private def conditionIntToString(v: Int): String = if (v === 100) "Any" else v.toString

private def conditionDisplay[T](intValue: T => Option[Int], label: T => String): Display[T] =
  Display.by(intValue(_).map(conditionIntToString).getOrElse("Unknown"), label)

given Display[CloudCover]    = conditionDisplay(_.toInt, _.label)
given Display[ImageQuality]  = conditionDisplay(_.toInt, _.label)
given Display[SkyBackground] = conditionDisplay(_.toInt, _.label)
given Display[WaterVapor]    = conditionDisplay(_.toInt, _.label)

given Display[Resource] = Display.byShortName(_.label)
