// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package giapi.client.commands

import cats.kernel.laws.discipline.EqTests
import cats.kernel.laws.discipline.MonoidTests
import giapi.client.GiapiArbitraries

/**
 * Tests Command typeclasses
 */
final class CommandsSuite extends munit.DisciplineSuite with GiapiArbitraries {
  checkAll("Eq[Configuration]", EqTests[Configuration].eqv)
  checkAll("Monoid[Configuration]", MonoidTests[Configuration].monoid)
}
