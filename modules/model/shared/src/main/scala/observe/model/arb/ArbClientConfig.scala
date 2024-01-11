// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.enums.ExecutionEnvironment
import lucuma.core.enums.Site
import lucuma.core.util.arb.ArbEnumerated.given
import observe.model.ClientConfig
import observe.model.ClientId
import observe.model.Version
import observe.model.arb.ObserveModelArbitraries.given
import org.http4s.Uri
import org.http4s.laws.discipline.arbitrary.given
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen

trait ArbClientConfig:
  given Arbitrary[ClientConfig] = Arbitrary:
    for
      site        <- arbitrary[Site]
      environment <- arbitrary[ExecutionEnvironment]
      odbUri      <- arbitrary[Uri]
      ssoUri      <- arbitrary[Uri]
      clientId    <- arbitrary[ClientId]
      version     <- arbitrary[Version]
    yield ClientConfig(site, environment, odbUri, ssoUri, clientId, version)

  given Cogen[ClientConfig] =
    Cogen[(Site, ClientId, Version)].contramap(x => (x.site, x.clientId, x.version))

object ArbClientConfig extends ArbClientConfig
