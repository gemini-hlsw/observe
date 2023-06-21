// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.flamingos2

import cats.kernel.laws.discipline.*
import cats.tests.CatsSuite
import observe.server.flamingos2.Flamingos2Controller.FocalPlaneUnit
import observe.server.*
import edu.gemini.spModel.gemini.flamingos2.Flamingos2.FPUnit

/**
 * Tests F2 typeclasses
 */
final class Flamingos2Spec extends CatsSuite with Flamingos2Arbitraries {
  checkAll("Eq[FPUnit]", EqTests[FPUnit].eqv)
  checkAll("Eq[FocalPlaneUnit]", EqTests[FocalPlaneUnit].eqv)
}
