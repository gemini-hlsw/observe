// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gcal

import cats.kernel.laws.discipline.*
import observe.server.gcal.GcalController.*

/**
 * Tests Gcal typeclasses
 */
class GcalSuite extends munit.DisciplineSuite with GcalArbitraries {
  checkAll("Eq[LampState]", EqTests[LampState].eqv)
  checkAll("Eq[ArLampState]", EqTests[ArLampState].eqv)
  checkAll("Eq[CuArLampState]", EqTests[CuArLampState].eqv)
  checkAll("Eq[QH5WLampState]", EqTests[QH5WLampState].eqv)
  checkAll("Eq[QH100WLampState]", EqTests[QH100WLampState].eqv)
  checkAll("Eq[ThArLampState]", EqTests[ThArLampState].eqv)
  checkAll("Eq[XeLampState]", EqTests[XeLampState].eqv)
  checkAll("Eq[IrLampState]", EqTests[IrLampState].eqv)
}
