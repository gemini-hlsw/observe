// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.enums.Site
import lucuma.core.util.arb.ArbEnumerated.given
import observe.model.ClientId
import observe.model.Conditions
import observe.model.ObserveModelArbitraries.given
import observe.model.arb.ArbClientId.given
import observe.model.events.client.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait ArbClientEvent:

  given Arbitrary[ClientEvent.ObserveState] = Arbitrary:
    arbitrary[Conditions].map(ClientEvent.ObserveState(_))

  given Cogen[ClientEvent.ObserveState] = Cogen[Conditions].contramap(_.conditions)

  given Arbitrary[ClientEvent.InitialEvent] = Arbitrary:
    for
      site     <- arbitrary[Site]
      clientId <- arbitrary[ClientId]
      version  <- arbitrary[String]
    yield ClientEvent.InitialEvent(site, clientId, version)

  given Cogen[ClientEvent.InitialEvent] =
    Cogen[(Site, ClientId, String)].contramap(x => (x.site, x.clientId, x.version))

  given Arbitrary[ClientEvent] = Arbitrary:
    for
      initial <- arbitrary[ClientEvent.InitialEvent]
      state   <- arbitrary[ClientEvent.ObserveState]
      r       <- Gen.oneOf(initial, state)
    yield r

  given Cogen[ClientEvent] =
    Cogen[Either[ClientEvent.InitialEvent, ClientEvent.ObserveState]].contramap:
      case e: ClientEvent.InitialEvent => Left(e)
      case e: ClientEvent.ObserveState => Right(e)

end ArbClientEvent

object ArbClientEvent extends ArbClientEvent
