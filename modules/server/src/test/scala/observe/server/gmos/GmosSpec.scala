// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.kernel.laws.discipline._
import cats.tests.CatsSuite
import lucuma.core.util.arb.ArbEnumerated._
import observe.server.gmos.GmosController.Config._

/**
 * Tests Gmos Config typeclasses
 */
final class GmosSpec extends CatsSuite {
  checkAll("Eq[ShutterState]", EqTests[ShutterState].eqv)
  checkAll("Eq[Beam]", EqTests[Beam].eqv)
}
