// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.core.model.sequence.gmos.arb.ArbDynamicConfig.given
import observe.model.InstrumentDynamicConfig
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait ArbInstrumentDynamicConfig:
  given Arbitrary[InstrumentDynamicConfig] = Arbitrary:
    Gen.oneOf(
      arbitrary[DynamicConfig.GmosNorth].map(InstrumentDynamicConfig.GmosNorth(_)),
      arbitrary[DynamicConfig.GmosSouth].map(InstrumentDynamicConfig.GmosSouth(_))
    )

  given Cogen[InstrumentDynamicConfig] =
    Cogen[Either[DynamicConfig.GmosNorth, DynamicConfig.GmosSouth]].contramap:
      case InstrumentDynamicConfig.GmosNorth(c) => Left(c)
      case InstrumentDynamicConfig.GmosSouth(c) => Right(c)

object ArbInstrumentDynamicConfig extends ArbInstrumentDynamicConfig
