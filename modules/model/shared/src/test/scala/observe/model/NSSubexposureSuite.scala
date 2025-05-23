// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.syntax.eq.*
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class NsSubexposureSuite extends munit.DisciplineSuite {
  test("subexposures calculations") {
    forAll(Gen.posNum[Int]) { n =>
      assert(NsSubexposure.subexposures(n).length === n * 4)
    }
  }
}
