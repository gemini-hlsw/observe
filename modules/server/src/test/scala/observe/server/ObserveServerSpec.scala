// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.kernel.laws.discipline.*
import cats.tests.CatsSuite
import lucuma.core.util.arb.ArbEnumerated.*

/**
 * Tests ObserveServer typeclasses
 */
final class ObserveServerSpec extends CatsSuite with ObserveServerArbitraries {
  checkAll("Eq[EpicsHealth]", EqTests[EpicsHealth].eqv)
}
