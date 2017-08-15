// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem

import cats.{ Eq, Show }
import cats.kernel.laws._
import cats.tests.CatsSuite
import gem.arb._

@SuppressWarnings(Array("org.wartremover.warts.ToString", "org.wartremover.warts.Equals"))
final class ObservationIdSpec extends CatsSuite {
  import ArbObservation._

  // Laws
  checkAll("Observation.Id", OrderLaws[Observation.Id].order)

  test("Equality must be natural") {
    forAll { (a: Observation.Id, b: Observation.Id) =>
      a.equals(b) shouldEqual Eq[Observation.Id].eqv(a, b)
    }
  }

  test("Equalty must act pairwise") {
    forAll { (a: Observation.Id, b: Observation.Id) =>
      Eq[Program.Id].eqv(a.pid, b.pid) &&
      Eq[Int].eqv(a.index, b.index) shouldEqual Eq[Observation.Id].eqv(a, b)
    }
  }

  test("Show must be natural") {
    forAll { (a: Observation.Id) =>
      a.toString shouldEqual Show[Observation.Id].show(a)
    }
  }

  test(".format must reparse") {
    forAll { (a: Observation.Id) =>
      Observation.Id.fromString(a.format) shouldEqual Some(a)
    }
  }

}
