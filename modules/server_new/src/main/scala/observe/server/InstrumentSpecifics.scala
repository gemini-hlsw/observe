// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import lucuma.core.enums.{LightSinkName, ObserveClass}
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.gmos.{DynamicConfig, StaticConfig}
import observe.model.enums.Instrument
import squants.Length

trait InstrumentSpecifics[S <: StaticConfig, D <: DynamicConfig] extends InstrumentGuide {
  def calcStepType(
    stepConfig:   StepConfig,
    staticConfig: S,
    instConfig:   D,
    obsClass:     ObserveClass
  ): Either[ObserveFailure, StepType] =
    SeqTranslate.calcStepType(instrument, stepConfig, obsClass)

  override val oiOffsetGuideThreshold: Option[Length] = None

  // The name used for this instrument in the science fold configuration
  def sfName(instConfig: D): LightSinkName

}
