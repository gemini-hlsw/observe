// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.all.*
import lucuma.core.util.Enumerated

enum ImageQuality(val toInt: Option[Int], val label: String) derives Enumerated:
  case Unknown   extends ImageQuality(none, "Unknown")
  case Percent20 extends ImageQuality(20.some, "20%/Best")
  case Percent70 extends ImageQuality(70.some, "70%/Good")
  case Percent85 extends ImageQuality(85.some, "85%/Poor")
  case Any       extends ImageQuality(100.some, "Any")

  val tag = label
