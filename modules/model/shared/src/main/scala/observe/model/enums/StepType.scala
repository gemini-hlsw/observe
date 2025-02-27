// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.*
import cats.syntax.all.*
import lucuma.core.util.Enumerated

enum StepType(val tag: String, val label: String) derives Enumerated {

  case Object            extends StepType("Object", "OBJECT")
  case Arc               extends StepType("Arc", "ARC")
  case Flat              extends StepType("Flat", "FLAT")
  case Bias              extends StepType("Bias", "BIAS")
  case Dark              extends StepType("Dark", "DARK")
  case Calibration       extends StepType("Calibration", "CAL")
  case AlignAndCalib     extends StepType("AlignAndCalib", "A & C")
  case NodAndShuffle     extends StepType("NodAndShuffle", "N & S")
  case NodAndShuffleDark extends StepType("NodAndShuffleDark", "N&S DARK")
}

object StepType {

  given Show[StepType] =
    Show.show(_.label)

  def fromString(s: String): Option[StepType] =
    Enumerated[StepType].all.find(_.label === s)

}
