// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.kernel.laws.discipline.*
import coulomb.*
import coulomb.syntax.*
import coulomb.units.accepted.ArcSecond
import coulomb.units.accepted.Millimeter
import edu.gemini.observe.server.tcs.BinaryOnOff
import edu.gemini.observe.server.tcs.BinaryYesNo
import lucuma.core.syntax.all.*
import observe.server.tcs.FocalPlaneScale.*
import observe.server.tcs.TcsController.FocalPlaneOffset
import observe.server.tcs.TcsController.InstrumentOffset
import observe.server.tcs.TcsController.OffsetP
import observe.server.tcs.TcsController.OffsetQ
import observe.server.tcs.TcsController.OffsetX
import observe.server.tcs.TcsController.OffsetY

/**
 * Tests Tcs typeclasses
 */
class TcsSuite extends munit.DisciplineSuite with TcsArbitraries:
  checkAll("Eq[BinaryYesNo]", EqTests[BinaryYesNo].eqv)
  checkAll("Eq[BinaryOnOff]", EqTests[BinaryOnOff].eqv)
  checkAll("Eq[CRFollow]", EqTests[CRFollow].eqv)

  assertEquals(pwfs1OffsetThreshold, 0.006205629747306757.withUnit[Millimeter])
  assertEquals(pwfs2OffsetThreshold, 0.006205629747306757.withUnit[Millimeter])
  assertEquals(AoOffsetThreshold, 0.006205629747306757.withUnit[Millimeter])
  assertEquals(0.01.withUnit[Millimeter] :* FOCAL_PLANE_SCALE, 0.0161144.withUnit[ArcSecond])
  assertEquals(0.032.withUnit[ArcSecond] :\ FOCAL_PLANE_SCALE,
               0.019858015191381622.withUnit[Millimeter]
  )

  test("Sanity checks") {
    val skyOffset =
      InstrumentOffset(OffsetP(30.0.withUnit[ArcSecond]), OffsetQ(30.0.withUnit[ArcSecond]))
        .toFocalPlaneOffset(0.arcseconds)
    assertEquals(
      FocalPlaneOffset(OffsetX(-18.61688924192027.withUnit[Millimeter]),
                       OffsetY(-18.61688924192027.withUnit[Millimeter])
      ),
      skyOffset
    )

    val fpo = FocalPlaneOffset(OffsetX(-18.61688924192027.withUnit[Millimeter]),
                               OffsetY(-18.61688924192027.withUnit[Millimeter])
    ).toInstrumentOffset(30.arcseconds)

    assertEquals(
      InstrumentOffset(
        OffsetP(29.99563635957558.withUnit[ArcSecond]),
        OffsetQ(30.004363005804787.withUnit[ArcSecond])
      ),
      fpo
    )
  }
