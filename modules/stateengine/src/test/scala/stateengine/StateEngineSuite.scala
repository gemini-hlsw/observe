// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package stateengine

import cats.effect.IO
import cats.effect.std.Semaphore
import cats.syntax.all.*
import munit.CatsEffectSuite

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class StateEngineSuite extends CatsEffectSuite {

  test("StateEngine should process commands") {
    assertIO(
      for {
        eng <- StateEngine.build[IO, Int, String]
        _   <- eng.offer(
                 eng
                   .modifyState(x => x + 1)
                   .flatMap(_ => eng.lift(0.toString.pure[IO])) *>
                   eng.getState.flatMap(x => eng.lift(x.toString.pure[IO])) *>
                   eng.setState(5) *>
                   eng.getState.flatMap(x => eng.lift(x.toString.pure[IO]))
               )
        out <- eng.process(0).take(3).compile.toList
      } yield out,
      List("0", "1", "5")
    )
  }

  test("StateEngine should not stall") {
    for {
      eng         <- StateEngine.build[IO, Int, String]
      startedFlag <- Semaphore.apply[IO](0)
      finishFlag  <- Semaphore.apply[IO](0)
      _           <- eng.offer(eng.lift(startedFlag.release *> finishFlag.acquire.as("X")))
      _           <- eng.offer(eng.lift(startedFlag.acquire *> finishFlag.release.as("Y")))
      out         <- eng.process(0).take(2).timeout(FiniteDuration(5, TimeUnit.SECONDS)).compile.toList
    } yield {
      assert(out.contains("X"))
      assert(out.contains("Y"))
    }
  }

}
