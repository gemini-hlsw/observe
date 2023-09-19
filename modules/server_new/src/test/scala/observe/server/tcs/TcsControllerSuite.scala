// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.kernel.laws.discipline.*
import observe.server.tcs.TcsController.*

/**
 * Tests TcsController typeclasses
 */
class TcsControllerSuite extends munit.DisciplineSuite with TcsArbitraries {
  checkAll("Eq[Beam]", EqTests[Beam].eqv)
  checkAll("Eq[NodAndChop]", EqTests[NodChop].eqv)
  checkAll("Eq[InstrumentOffset]", EqTests[InstrumentOffset].eqv)
}
