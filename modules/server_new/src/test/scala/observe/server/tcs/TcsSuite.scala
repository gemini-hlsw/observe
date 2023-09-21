// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.kernel.laws.discipline.*
import edu.gemini.observe.server.tcs.{BinaryOnOff, BinaryYesNo}

/**
 * Tests Tcs typeclasses
 */
class TcsSuite extends munit.DisciplineSuite with TcsArbitraries {
  checkAll("Eq[BinaryYesNo]", EqTests[BinaryYesNo].eqv)
  checkAll("Eq[BinaryOnOff]", EqTests[BinaryOnOff].eqv)
  checkAll("Eq[CRFollow]", EqTests[CRFollow].eqv)
}
