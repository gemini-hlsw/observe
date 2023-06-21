// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.tests.CatsSuite
import monocle.law.discipline.LensTests
import observe.model.ObserveModelArbitraries.{*, given}
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*

final class ExecutionQueueSpec extends CatsSuite with ObserveServerArbitraries {
  checkAll("ExecutionQueue name lens", LensTests(ExecutionQueue.name))
  checkAll("ExecutionQueue command state lens", LensTests(ExecutionQueue.cmdState))
  checkAll("ExecutionQueue queue lens", LensTests(ExecutionQueue.queue))
}
