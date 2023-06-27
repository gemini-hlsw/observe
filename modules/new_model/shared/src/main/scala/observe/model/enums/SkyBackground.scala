// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.syntax.all.*
import lucuma.core.util.Enumerated

enum SkyBackground(val toInt: Option[Int], val label: String) derives Enumerated:
  case Unknown   extends SkyBackground(none, "Unknown")
  case Percent20 extends SkyBackground(20.some, "20%/Darkest")
  case Percent50 extends SkyBackground(50.some, "50%/Dark")
  case Percent80 extends SkyBackground(80.some, "80%/Grey")
  case Any       extends SkyBackground(100.some, "Any/Bright")

  val tag = label
