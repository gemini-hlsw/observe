// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.syntax.all.*
import coulomb.Quantity
import coulomb.units.accepted.Millimeter
import lucuma.core.enums.Instrument
import lucuma.core.enums.LightSinkName
import lucuma.core.enums.StepGuideState
import lucuma.core.math.Offset
import lucuma.core.model.sequence.TelescopeConfig
import lucuma.schemas.ObservationDB.Enums.GuideProbe
import observe.common.ObsQueriesGQL.ObsQuery.Data
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment.GuideEnvironment
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.TargetEnvironment.GuideEnvironment.GuideTargets
import observe.server.InstrumentGuide
import observe.server.tcs.TcsController.LightPath
import observe.server.tcs.TcsController.LightSource

class TcsSouthSuite extends munit.FunSuite {

  test("SeqTranslate extracts guide state") {
    // OIWFS target and guide enabled
    assertEquals(
      TcsSouth
        .config(
          new InstrumentGuide {
            override def instrument: Instrument                                       = Instrument.GmosSouth
            override def oiOffsetGuideThreshold: Option[Quantity[Double, Millimeter]] = none
          },
          Data.Observation
            .TargetEnvironment(none, GuideEnvironment(List(GuideTargets(GuideProbe.GmosOiwfs)))),
          TelescopeConfig.Default,
          LightPath(LightSource.Sky, LightSinkName.Gmos),
          none
        )
        .guideWithOI,
      StepGuideState.Enabled.some
    )
    // No OIWFS target
    assertEquals(
      TcsSouth
        .config(
          new InstrumentGuide {
            override def instrument: Instrument = Instrument.GmosSouth

            override def oiOffsetGuideThreshold: Option[Quantity[Double, Millimeter]] = none
          },
          Data.Observation
            .TargetEnvironment(none, GuideEnvironment(List.empty)),
          TelescopeConfig.Default,
          LightPath(LightSource.Sky, LightSinkName.Gmos),
          none
        )
        .guideWithOI,
      none
    )
    // OIWFS target but guide disabled
    assertEquals(
      TcsSouth
        .config(
          new InstrumentGuide {
            override def instrument: Instrument                                       = Instrument.GmosSouth
            override def oiOffsetGuideThreshold: Option[Quantity[Double, Millimeter]] = none
          },
          Data.Observation
            .TargetEnvironment(none, GuideEnvironment(List(GuideTargets(GuideProbe.GmosOiwfs)))),
          TelescopeConfig(Offset.Zero, StepGuideState.Disabled),
          LightPath(LightSource.Sky, LightSinkName.Gmos),
          none
        )
        .guideWithOI,
      StepGuideState.Disabled.some
    )

  }

}
