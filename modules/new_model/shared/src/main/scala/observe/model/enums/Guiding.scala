// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import lucuma.core.util.Enumerated

enum Guiding(val configValue: String) derives Eq:
  case Guide  extends Guiding("guide")
  case Park   extends Guiding("park")
  case Freeze extends Guiding("freeze")

object Guiding:
  def fromString(s: String): Option[Guiding] =
    values.find(_.configValue === s)
