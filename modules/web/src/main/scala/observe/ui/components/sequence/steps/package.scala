// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import lucuma.ui.sequence.SequenceRow
import observe.model.enums.ExecutionStepType
import lucuma.core.model.sequence.StepConfig
import lucuma.core.enums.SmartGcalType
import cats.syntax.option.*

extension [D](row: SequenceRow[D])
  def stepType(isNodAndShuffle: Boolean): Option[ExecutionStepType] =
    (row.stepConfig, isNodAndShuffle) match
      case (Some(StepConfig.Bias), _)                          => ExecutionStepType.Bias.some
      case (Some(StepConfig.Dark), true)                       => ExecutionStepType.NodAndShuffleDark.some
      case (Some(StepConfig.Dark), _)                          => ExecutionStepType.Dark.some
      case (Some(StepConfig.Gcal(_, _, _, _)), _)              => ExecutionStepType.Calibration.some
      case (Some(StepConfig.Science(_, _)), true)              => ExecutionStepType.NodAndShuffle.some
      case (Some(StepConfig.Science(_, _)), _)                 => ExecutionStepType.Object.some
      case (Some(StepConfig.SmartGcal(SmartGcalType.Arc)), _)  => ExecutionStepType.Arc.some
      case (Some(StepConfig.SmartGcal(SmartGcalType.Flat)), _) => ExecutionStepType.Flat.some
      case (Some(StepConfig.SmartGcal(_)), _)                  => none // Unknown SmartGcal type
      case _                                                   => none
      // TODO ExecutionStepType.AlignAndCalib in GPI
