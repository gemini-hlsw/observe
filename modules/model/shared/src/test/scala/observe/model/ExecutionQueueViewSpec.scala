// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import lucuma.core.util.arb.ArbEnumerated._
import lucuma.core.util.arb.ArbGid._
import monocle.law.discipline.LensTests
import observe.model.ObserveModelArbitraries._

final class ExecutionQueueViewSpec extends munit.DisciplineSuite {

  checkAll("ExecutionQueueView id lens", LensTests(ExecutionQueueView.id))
  checkAll("ExecutionQueueView name lens", LensTests(ExecutionQueueView.name))
  checkAll("ExecutionQueueView command state lens", LensTests(ExecutionQueueView.cmdState))
  checkAll("ExecutionQueueView execution state lens", LensTests(ExecutionQueueView.execState))
  checkAll("ExecutionQueueView queue lens", LensTests(ExecutionQueueView.queue))
}
