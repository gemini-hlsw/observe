// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.effect.IO
import edu.gemini.epics.acm.CaWindowStabilizer
import edu.gemini.epics.acm.test.DummyAttribute
import lucuma.core.util.TimeSpan

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

class EpicsUtilSuite extends munit.CatsEffectSuite {

  val executor: ScheduledExecutorService = new ScheduledThreadPoolExecutor(2)

  test("EpicsUtil waitForValue should work with CaWindowStabilizer") {
    val attr: DummyAttribute[Integer]         = new DummyAttribute[Integer]("dummy", "foo")
    val filtered: CaWindowStabilizer[Integer] =
      new CaWindowStabilizer(attr, Duration.ofMillis(50), executor)
    attr.setValue(1)
    IO.delay(filtered.restart) *>
      EpicsUtil.waitForValueF[Integer, IO](
        filtered,
        1,
        TimeSpan.unsafeFromDuration(100, ChronoUnit.MILLIS),
        ""
      )
  }

}
