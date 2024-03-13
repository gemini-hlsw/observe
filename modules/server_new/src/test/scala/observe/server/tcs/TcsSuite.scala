// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.kernel.laws.discipline.*
import edu.gemini.observe.server.tcs.BinaryOnOff
import edu.gemini.observe.server.tcs.BinaryYesNo
import squants.space.AngleConversions.*
import squants.space.LengthConversions.*
import observe.server.tcs.TcsController.InstrumentOffset
import observe.server.tcs.TcsController.OffsetP
import observe.server.tcs.TcsController.OffsetQ
import observe.server.tcs.TcsController.FocalPlaneOffset
import observe.server.tcs.TcsController.OffsetX
import observe.server.tcs.TcsController.OffsetY

/**
 * Tests Tcs typeclasses
 */
class TcsSuite extends munit.DisciplineSuite with TcsArbitraries {
  checkAll("Eq[BinaryYesNo]", EqTests[BinaryYesNo].eqv)
  checkAll("Eq[BinaryOnOff]", EqTests[BinaryOnOff].eqv)
  checkAll("Eq[CRFollow]", EqTests[CRFollow].eqv)

  test("Sanity checks") {
    val skyOffset = InstrumentOffset(OffsetP(30.arcseconds), OffsetQ(30.arcseconds))
      .toFocalPlaneOffset(0.arcseconds)
    assertEquals(
      FocalPlaneOffset(OffsetX(-18.61688924192027.millimeters),
                       OffsetY(-18.61688924192027.millimeters)
      ),
      skyOffset
    )
  }
}
