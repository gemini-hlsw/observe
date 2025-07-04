// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.config.arb

import lucuma.core.util.arb.ArbEnumerated.given
import observe.model.config.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen

trait ArbSystemsControlConfiguration {

  given Arbitrary[SystemsControlConfiguration] =
    Arbitrary {
      for {
        altair   <- arbitrary[ControlStrategy]
        gems     <- arbitrary[ControlStrategy]
        dhs      <- arbitrary[ControlStrategy]
        f2       <- arbitrary[ControlStrategy]
        gcal     <- arbitrary[ControlStrategy]
        gmos     <- arbitrary[ControlStrategy]
        gnirs    <- arbitrary[ControlStrategy]
        gpi      <- arbitrary[ControlStrategy]
        gpiGds   <- arbitrary[ControlStrategy]
        ghost    <- arbitrary[ControlStrategy]
        ghostGds <- arbitrary[ControlStrategy]
        gsaoi    <- arbitrary[ControlStrategy]
        gws      <- arbitrary[ControlStrategy]
        nifs     <- arbitrary[ControlStrategy]
        niri     <- arbitrary[ControlStrategy]
        tcs      <- arbitrary[ControlStrategy]
      } yield SystemsControlConfiguration(altair,
                                          gems,
                                          dhs,
                                          f2,
                                          gcal,
                                          gmos,
                                          gnirs,
                                          gpi,
                                          gpiGds,
                                          ghost,
                                          ghostGds,
                                          gsaoi,
                                          gws,
                                          nifs,
                                          niri,
                                          tcs
      )
    }

  given Cogen[SystemsControlConfiguration] =
    Cogen[
      (
        ControlStrategy,
        ControlStrategy,
        ControlStrategy,
        ControlStrategy,
        ControlStrategy,
        ControlStrategy,
        ControlStrategy,
        ControlStrategy,
        ControlStrategy,
        ControlStrategy,
        ControlStrategy,
        ControlStrategy,
        ControlStrategy,
        ControlStrategy,
        ControlStrategy,
        ControlStrategy
      )
    ].contramap(x =>
      (x.altair,
       x.gems,
       x.dhs,
       x.flamingos2,
       x.gcal,
       x.gmos,
       x.gnirs,
       x.gpi,
       x.gpiGds,
       x.ghost,
       x.ghostGds,
       x.gsaoi,
       x.gws,
       x.nifs,
       x.niri,
       x.tcs
      )
    )

}

object ArbSystemsControlConfiguration extends ArbSystemsControlConfiguration
