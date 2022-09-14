// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.eq.*
import lucuma.core.util.Display

private def conditionIntToString(v: Int): String = if (v === 100) "Any" else v.toString

private def conditionDisplay[T](intValue: T => Option[Int], label: T => String): Display[T] =
  Display.by(intValue(_).map(conditionIntToString).getOrElse("Unknown"), label)

given Display[CloudCover]    = conditionDisplay(_.toInt, _.label)
given Display[ImageQuality]  = conditionDisplay(_.toInt, _.label)
given Display[SkyBackground] = conditionDisplay(_.toInt, _.label)
given Display[WaterVapor]    = conditionDisplay(_.toInt, _.label)
