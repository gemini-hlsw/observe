// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.*
import cats.syntax.all.*
import lucuma.core.util.Enumerated

sealed abstract class StepType(val tag: String, val label: String) extends Product with Serializable

object StepType {

  case object Object            extends StepType("Object", "OBJECT")
  case object Arc               extends StepType("Arc", "ARC")
  case object Flat              extends StepType("Flat", "FLAT")
  case object Bias              extends StepType("Bias", "BIAS")
  case object Dark              extends StepType("Dark", "DARK")
  case object Calibration       extends StepType("Calibration", "CAL")
  case object AlignAndCalib     extends StepType("AlignAndCalib", "A & C")
  case object NodAndShuffle     extends StepType("NodAndShuffle", "N & S")
  case object NodAndShuffleDark extends StepType("NodAndShuffleDark", "N&S DARK")

  given Show[StepType] =
    Show.show(_.label)

  def fromString(s: String): Option[StepType] =
    Enumerated[StepType].all.find(_.label === s)

  /** @group Typeclass Instances */
  given Enumerated[StepType] =
    Enumerated
      .from(Object,
            Arc,
            Flat,
            Bias,
            Dark,
            Calibration,
            AlignAndCalib,
            NodAndShuffle,
            NodAndShuffleDark
      )
      .withTag(_.tag)

}
