// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import observe.model.ClientId
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Cogen.*

import java.util.UUID

trait ArbClientId {

  given clientIdArb: Arbitrary[ClientId] = Arbitrary {
    arbitrary[UUID].map(ClientId.apply)
  }

  given cogenUUID: Cogen[UUID] =
    Cogen[(Long, Long)].contramap(u => (u.getMostSignificantBits, u.getLeastSignificantBits))

  given cidCogen: Cogen[ClientId] =
    Cogen[UUID].contramap(_.self)

}

object ArbClientId extends ArbClientId
