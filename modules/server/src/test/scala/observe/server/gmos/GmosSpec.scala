// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.kernel.laws.discipline.*
import cats.tests.CatsSuite
import lucuma.core.util.arb.ArbEnumerated.*
import observe.server.gmos.GmosController.Config.*

/**
 * Tests Gmos Config typeclasses
 */
final class GmosSpec extends CatsSuite {
  checkAll("Eq[ShutterState]", EqTests[ShutterState].eqv)
  checkAll("Eq[Beam]", EqTests[Beam].eqv)
}
