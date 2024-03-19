// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.kernel.laws.discipline.*
import lucuma.core.util.arb.ArbEnumerated.given

/**
 * Tests ObserveServer typeclasses
 */
final class ObserveServerSuite extends munit.DisciplineSuite with ObserveServerArbitraries {
  checkAll("Eq[EpicsHealth]", EqTests[EpicsHealth].eqv)
}
