// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.enums

import cats.Eq
import cats.derived.*

enum ObsClass derives Eq:
  case Daytime, Nighttime

object ObsClass:
  def fromString(s: String): ObsClass = s match
    case "dayCal" => ObsClass.Daytime
    case _        => ObsClass.Nighttime
