// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.syntax.option.*
import coulomb.*
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import lucuma.core.enums.Instrument
import lucuma.core.util.TimeSpan
import observe.server.tcs.TcsController.OffsetP
import observe.server.tcs.TcsController.OffsetQ

class TcsSettleTimeCalculatorSuite extends munit.DisciplineSuite {
  test("calcDisplacement") {
    val startOffset =
      TcsController.InstrumentOffset(OffsetP(1.0.withUnit[ArcSecond]),
                                     OffsetQ(1.0.withUnit[ArcSecond])
      )
    val endOffset   =
      TcsController.InstrumentOffset(
        OffsetP(2.0.withUnit[ArcSecond]),
        OffsetQ(2.0.withUnit[ArcSecond])
      )

    val expected = 1.4142135623730951.withUnit[ArcSecond]
    val result   = TcsSettleTimeCalculator.calcDisplacement(startOffset, endOffset)
    assertEquals(result, expected)
  }

  test("calcDisplacement reverse") {
    val endOffset =
      TcsController.InstrumentOffset(OffsetP(1.0.withUnit[ArcSecond]),
                                     OffsetQ(1.0.withUnit[ArcSecond])
      )

    val startOffset =
      TcsController.InstrumentOffset(OffsetP(2.0.withUnit[ArcSecond]),
                                     OffsetQ(2.0.withUnit[ArcSecond])
      )

    val expected = 1.4142135623730951.withUnit[ArcSecond]
    val result   = TcsSettleTimeCalculator.calcDisplacement(startOffset, endOffset)
    assertEquals(result, expected)
  }

  test("settle time calculator mount") {
    val displacement = 1.0.withUnit[ArcSecond]
    val expected     = TimeSpan.fromSeconds(1.0)
    val result       = TcsSettleTimeCalculator
      .settleTimeCalculators(TcsController.Subsystem.Mount)
      .calc(displacement)
    assertEquals(result.some, expected)
  }

  test("settle time calculator pwfs1") {
    val displacement = 0.2.withUnit[ArcSecond]
    val expected     = TimeSpan.fromSeconds(1.0)
    val result       = TcsSettleTimeCalculator
      .settleTimeCalculators(TcsController.Subsystem.PWFS1)
      .calc(displacement)
    assertEquals(result.some, expected)
  }

  test("settle time gmos oiwfs") {
    val displacement = 0.2.withUnit[ArcSecond]
    val expected     = TimeSpan.fromSeconds(1.0)
    val result       = TcsSettleTimeCalculator
      .oiwfsSettleTimeCalculators(Instrument.GmosNorth)
      .calc(displacement)
    assertEquals(result.some, expected)
  }

  test("settle time gmos-south oiwfs") {
    val displacement = 0.2.withUnit[ArcSecond]
    val expected     = TimeSpan.fromSeconds(1.0)
    val result       = TcsSettleTimeCalculator
      .oiwfsSettleTimeCalculators(Instrument.GmosSouth)
      .calc(displacement)
    assertEquals(result.some, expected)
  }
}
