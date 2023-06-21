// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.common

import cats.kernel.laws.discipline.EqTests
import cats.laws.discipline.{FoldableTests, FunctorTests, TraverseTests}
import cats.tests.CatsSuite
import observe.common.ArbitrariesCommon.given

/**
 * Tests the Monocle Lenses for Observe Events
 */
final class FixedLengthBufferSpec extends CatsSuite {

  checkAll("Eq[FixedLengthBuffer]", EqTests[FixedLengthBuffer[Int]].eqv)
  checkAll("Functor[FixedLengthBuffer]", FunctorTests[FixedLengthBuffer].functor[Int, Int, Int])
  checkAll("Foldable[FixedLengthBuffer]", FoldableTests[FixedLengthBuffer].foldable[Int, Int])
  checkAll("Traversable[FixedLengthBuffer]",
           TraverseTests[FixedLengthBuffer].traverse[Int, Int, Int, Int, Option, Option]
  )
}
