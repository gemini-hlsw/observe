// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import cats.kernel.laws.discipline._
import cats.tests.CatsSuite
// import monocle.law.discipline.PrismTests
import observe.web.client.model.Pages._
import lucuma.core.util.arb.ArbGid._
import lucuma.core.util.arb.ArbUid._

/**
 * Tests Client typeclasses
 */
final class PagesSpec extends CatsSuite with ArbitrariesWebClient {

  checkAll("Eq[ObservePages]", EqTests[ObservePages].eqv)
  checkAll("Eq[StepIdDisplayed]", EqTests[StepIdDisplayed].eqv)

  // lenses
  // checkAll("Prism[Action, ObservePages]", PrismTests(PageActionP))
}
