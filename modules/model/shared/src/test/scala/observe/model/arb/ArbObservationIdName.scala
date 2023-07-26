// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import observe.model.Observation
import org.scalacheck.{Arbitrary, Cogen}
import org.scalacheck.Arbitrary.*
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*

trait ArbObservationIdName {
  given arbObservationIdName: Arbitrary[Observation.Id] = Arbitrary[Observation.Id] {
    for {
      id   <- arbitrary[lucuma.core.model.Observation.Id]
      name <- arbitrary[Observation.Name]
    } yield Observation.Id(id, name)
  }

  given cogenObservationIdName: Cogen[Observation.Id] =
    Cogen[(Observation.Id, Observation.Name)].contramap(x => (x.id, x.name))
}

object ArbObservationIdName extends ArbObservationIdName
