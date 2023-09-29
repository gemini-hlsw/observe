// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import lucuma.core.enums.LightSinkName.F2
import lucuma.core.enums.LightSinkName.Gmos
import lucuma.core.enums.LightSinkName.Gsaoi
import lucuma.core.enums.LightSinkName.Nifs
import lucuma.core.enums.LightSinkName.Niri_f32
import observe.server.EpicsCodex.*
import observe.server.tcs.TcsController.LightSource.AO
import observe.server.tcs.TcsController.LightSource.GCAL
import observe.server.tcs.TcsController.LightSource.Sky

import ScienceFoldPositionCodex.given

class ScienceFoldPositionCodexSuite extends munit.FunSuite {

  private val invalid    = "Invalid"
  private val parked     = ("park-pos.", ScienceFold.Parked)
  private val ao2gmos3   = ("ao2gmos3", ScienceFold.Position(AO, Gmos, 3))
  private val gcal2nifs1 = ("gcal2nifs1", ScienceFold.Position(GCAL, Nifs, 1))
  private val gsaoi5     = ("gsaoi5", ScienceFold.Position(Sky, Gsaoi, 5))
  private val ao2niri32  = ("ao2nirif32p5", ScienceFold.Position(AO, Niri_f32, 5))
  private val f21        = ("f21", ScienceFold.Position(Sky, F2, 1))
  private val testVals   = List(ao2gmos3, gcal2nifs1, gsaoi5, ao2niri32, f21)

  test("ScienceFoldPositionCodex should properly decode EPICS strings into ScienceFold values") {

    testVals.foreach { case (s, v) =>
      assertEquals(decode[String, Option[ScienceFold]](s), Some(v))
    }

    assertEquals(decode[String, Option[ScienceFold]](invalid), None)

    assertEquals(decode[String, Option[ScienceFold]](parked._1), Some(parked._2))

  }

  test("ScienceFoldPositionCodex should properly encode Position values into EPICS strings") {

    assertEquals(decode[String, Option[ScienceFold]](invalid), None)

    testVals.foreach { case (s, v) =>
      assertEquals(encode[ScienceFold.Position, String](v), s)
    }

  }

}
