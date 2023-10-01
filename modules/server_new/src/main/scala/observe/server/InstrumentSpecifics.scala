// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import coulomb.Quantity
import coulomb.units.accepted.Millimeter
import lucuma.core.enums.LightSinkName
import lucuma.core.enums.ObserveClass
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.StaticConfig

trait InstrumentSpecifics[S <: StaticConfig, D <: DynamicConfig] extends InstrumentGuide {
  def calcStepType(
    stepConfig:   StepConfig,
    staticConfig: S,
    instConfig:   D,
    obsClass:     ObserveClass
  ): Either[ObserveFailure, StepType] =
    SeqTranslate.calcStepType(instrument, stepConfig, obsClass)

  override val oiOffsetGuideThreshold: Option[Quantity[Double, Millimeter]] = None

  // The name used for this instrument in the science fold configuration
  def sfName(instConfig: D): LightSinkName

}
