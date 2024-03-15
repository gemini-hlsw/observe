// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import coulomb.Quantity
import coulomb.units.accepted.Millimeter
import lucuma.core.enums.Instrument

trait InstrumentGuide {
  def instrument: Instrument
  def oiOffsetGuideThreshold: Option[Quantity[Double, Millimeter]]
}
