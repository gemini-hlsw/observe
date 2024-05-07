// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.syntax.either.*
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.syntax.*
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.gmos.DynamicConfig
import lucuma.odb.json.gmos.given
import lucuma.odb.json.time.transport.given
import lucuma.odb.json.wavelength.transport.given

// The whole purpose of this class is to bundle the instrument with its dynamic configuration,
// so that the dynamic configuration can be decoded correctly.
enum InstrumentDynamicConfig(val instrument: Instrument):
  def config: DynamicConfig

  case GmosNorth(config: DynamicConfig.GmosNorth)
      extends InstrumentDynamicConfig(Instrument.GmosNorth)

  case GmosSouth(config: DynamicConfig.GmosSouth)
      extends InstrumentDynamicConfig(Instrument.GmosSouth)

object InstrumentDynamicConfig:
  // object GmosNorth:
  //   given Eq[InstrumentDynamicConfig.GmosNorth] = Eq.by(x => (x.instrument, x.config))
  // object GmosSouth:
  //   given Eq[InstrumentDynamicConfig.GmosSouth] = Eq.by(x => (x.instrument, x.config))

  given Eq[InstrumentDynamicConfig] = Eq.by(x => (x.instrument, x.config))
  // case xInstrumentDynamicConfig.GmosNorth => x
  // case x: InstrumentDynamicConfig.GmosSouth => x

  given Encoder.AsObject[InstrumentDynamicConfig] = Encoder.AsObject.instance: idc =>
    JsonObject(
      "instrument" -> idc.instrument.asJson,
      "config"     -> (idc match
        case InstrumentDynamicConfig.GmosNorth(config) => config.asJson
        case InstrumentDynamicConfig.GmosSouth(config) => config.asJson
      )
    )

  given Decoder[InstrumentDynamicConfig] = Decoder.instance: c =>
    c.downField("instrument")
      .as[Instrument]
      .flatMap:
        case Instrument.GmosNorth =>
          c.downField("config")
            .as[DynamicConfig.GmosNorth]
            .map(InstrumentDynamicConfig.GmosNorth(_))
        case Instrument.GmosSouth =>
          c.downField("config")
            .as[DynamicConfig.GmosSouth]
            .map(InstrumentDynamicConfig.GmosSouth(_))
        case i                    =>
          DecodingFailure(
            s"Attempted to decode InstrumentDynamicConfig with unavailable instrument: $i",
            c.history
          ).asLeft

  def fromDynamicConfig(config: DynamicConfig): InstrumentDynamicConfig =
    config match
      case c @ DynamicConfig.GmosNorth(_, _, _, _, _, _, _) => InstrumentDynamicConfig.GmosNorth(c)
      case c @ DynamicConfig.GmosSouth(_, _, _, _, _, _, _) => InstrumentDynamicConfig.GmosSouth(c)
