// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import lucuma.core.enums.LightSinkName
import squants.Length

trait InstrumentSpecifics extends InstrumentGuide {
  override val oiOffsetGuideThreshold: Option[Length] = None

  // The name used for this instrument in the science fold configuration
  def sfName(config: CleanConfig): LightSinkName

}
