// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.syntax.all.*
import lucuma.core.util.Enumerated

enum EpicsHealth(val tag: String) derives Enumerated {

  case Good extends EpicsHealth("Good")

  case Bad extends EpicsHealth("Bad")
}

object EpicsHealth {
  def fromInt(v: Int): EpicsHealth = if (v === 0) Good else Bad
}
