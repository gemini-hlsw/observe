// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.enums.Site
import lucuma.core.util.arb.ArbEnumerated.given
import observe.model.ClientId
import observe.model.Environment
import observe.model.arb.ObserveModelArbitraries.given
import observe.model.Version
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen

trait ArbEnvironment:
  given Arbitrary[Environment] = Arbitrary:
    for
      site     <- arbitrary[Site]
      clientId <- arbitrary[ClientId]
      version  <- arbitrary[Version]
    yield Environment(site, clientId, version)

  given Cogen[Environment] =
    Cogen[(Site, ClientId, Version)].contramap(x => (x.site, x.clientId, x.version))

object ArbEnvironment extends ArbEnvironment
