// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import observe.model.Observation
import org.scalacheck.{Arbitrary, Cogen}
import org.scalacheck.Arbitrary._
import lucuma.core.util.arb.ArbGid._
import lucuma.core.util.arb.ArbUid._

trait ArbObservationIdName {
  implicit val arbObservationIdName: Arbitrary[Observation.IdName] = Arbitrary[Observation.IdName] {
    for {
      id   <- arbitrary[lucuma.core.model.Observation.Id]
      name <- arbitrary[Observation.Name]
    } yield Observation.IdName(id, name)
  }

  implicit val cogenObservationIdName: Cogen[Observation.IdName] =
    Cogen[(Observation.Id, Observation.Name)].contramap(x => (x.id, x.name))
}

object ArbObservationIdName extends ArbObservationIdName
