// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.all.*
import lucuma.core.util.Enumerated

enum CloudCover(val toInt: Option[Int], val label: String) derives Enumerated:
  case Unknown   extends CloudCover(none, "Unknown")
  case Percent50 extends CloudCover(50.some, "50%/Clear")
  case Percent70 extends CloudCover(70.some, "70%/Cirrus")
  case Percent80 extends CloudCover(80.some, "80%/Cloudy")
  case Any       extends CloudCover(100.some, "Any")

  val tag = label
