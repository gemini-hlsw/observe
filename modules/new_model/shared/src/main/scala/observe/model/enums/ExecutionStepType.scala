// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats.*
import cats.derived.*
import cats.syntax.all.*
import lucuma.core.util.Display
import lucuma.core.util.Enumerated

enum ExecutionStepType(val label: String) derives Eq:
  case Object            extends ExecutionStepType("OBJECT")
  case Arc               extends ExecutionStepType("ARC")
  case Flat              extends ExecutionStepType("FLAT")
  case Bias              extends ExecutionStepType("BIAS")
  case Dark              extends ExecutionStepType("DARK")
  case Calibration       extends ExecutionStepType("CAL")
  case AlignAndCalib     extends ExecutionStepType("A & C")
  case NodAndShuffle     extends ExecutionStepType("N & S")
  case NodAndShuffleDark extends ExecutionStepType("N&S DARK")

object ExecutionStepType:
  val fromLabel: Map[String, ExecutionStepType] =
    values.map(v => v.label -> v).toMap

  given Display[ExecutionStepType] = Display.byShortName(_.label)
