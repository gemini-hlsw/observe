// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.config

import cats.kernel.laws.discipline.*
import observe.model.config.arb.ArbSystemsControlConfiguration.given

/**
 * Tests config classes
 */
class ConfigModelSuite extends munit.DisciplineSuite {
  checkAll("Eq[SystemsControlConfiguration]", EqTests[SystemsControlConfiguration].eqv)
}
