// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.all.*
import lucuma.core.util.Enumerated

enum WaterVapor(val toInt: Option[Int], val label: String) derives Enumerated:
  case Unknown   extends WaterVapor(none, "Unknown")
  case Percent20 extends WaterVapor(20.some, "20%/Low")
  case Percent50 extends WaterVapor(50.some, "50%/Median")
  case Percent80 extends WaterVapor(80.some, "85%/High")
  case Any       extends WaterVapor(100.some, "Any")

  val tag = label
