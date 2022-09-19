// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.enums

import cats._
import cats.syntax.all._
import lucuma.core.util.Enumerated
import cats.derived.*

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
