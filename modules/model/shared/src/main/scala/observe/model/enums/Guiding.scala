// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.all.*
import lucuma.core.util.Enumerated

enum Guiding(val tag: String, val configValue: String) derives Enumerated:
  case Guide  extends Guiding("Guide", "guide")
  case Park   extends Guiding("Park", "park")
  case Freeze extends Guiding("Freeze", "freeze")

object Guiding:
  def fromString(s: String): Option[Guiding] =
    values.find(_.configValue === s)
