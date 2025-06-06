// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.syntax.either.*
import cats.syntax.eq.*
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.syntax.*
import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.flamingos2.Flamingos2DynamicConfig
import lucuma.core.model.sequence.gmos
import lucuma.odb.json.flamingos2.given
import lucuma.odb.json.gmos.given
import lucuma.odb.json.time.transport.given
import lucuma.odb.json.wavelength.transport.given

// The whole purpose of this class is to bundle the instrument with its dynamic configuration,
// so that the dynamic configuration can be decoded correctly.
sealed trait InstrumentDynamicConfig(val instrument: Instrument):
  type D

  def config: D

object InstrumentDynamicConfig:
  case class GmosNorth(config: gmos.DynamicConfig.GmosNorth)
      extends InstrumentDynamicConfig(Instrument.GmosNorth) {
    type D = gmos.DynamicConfig.GmosNorth
  }

  case class GmosSouth(config: gmos.DynamicConfig.GmosSouth)
      extends InstrumentDynamicConfig(Instrument.GmosSouth) {
    type D = gmos.DynamicConfig.GmosSouth
  }

  case class Flamingos2(config: Flamingos2DynamicConfig)
      extends InstrumentDynamicConfig(Instrument.Flamingos2) {
    type D = Flamingos2DynamicConfig
  }

  object GmosNorth:
    given Eq[GmosNorth] = Eq.by(x => (x.instrument, x.config))

  object GmosSouth:
    given Eq[GmosSouth] = Eq.by(x => (x.instrument, x.config))

  object Flamingos2:
    given Eq[Flamingos2] = Eq.by(x => (x.instrument, x.config))

  given Eq[InstrumentDynamicConfig] = Eq.instance:
    case (a: GmosNorth, b: GmosNorth)   => a === b
    case (a: GmosSouth, b: GmosSouth)   => a === b
    case (a: Flamingos2, b: Flamingos2) => a === b
    case _                              => false

  given Encoder.AsObject[InstrumentDynamicConfig] = Encoder.AsObject.instance: idc =>
    JsonObject(
      "instrument" -> idc.instrument.asJson,
      "config"     -> (idc match
        case InstrumentDynamicConfig.GmosNorth(config)  => config.asJson
        case InstrumentDynamicConfig.GmosSouth(config)  => config.asJson
        case InstrumentDynamicConfig.Flamingos2(config) => config.asJson
      )
    )

  given Decoder[InstrumentDynamicConfig] = Decoder.instance: c =>
    c.downField("instrument")
      .as[Instrument]
      .flatMap:
        case Instrument.GmosNorth  =>
          c.downField("config")
            .as[gmos.DynamicConfig.GmosNorth]
            .map(InstrumentDynamicConfig.GmosNorth(_))
        case Instrument.GmosSouth  =>
          c.downField("config")
            .as[gmos.DynamicConfig.GmosSouth]
            .map(InstrumentDynamicConfig.GmosSouth(_))
        case Instrument.Flamingos2 =>
          c.downField("config")
            .as[Flamingos2DynamicConfig]
            .map(InstrumentDynamicConfig.Flamingos2(_))
        case i                     =>
          DecodingFailure(
            s"Attempted to decode InstrumentDynamicConfig with unavailable instrument: $i",
            c.history
          ).asLeft

  def fromDynamicConfig[D](config: D): InstrumentDynamicConfig =
    config match
      case c @ gmos.DynamicConfig.GmosNorth(_, _, _, _, _, _, _)  =>
        InstrumentDynamicConfig.GmosNorth(c)
      case c @ gmos.DynamicConfig.GmosSouth(_, _, _, _, _, _, _)  =>
        InstrumentDynamicConfig.GmosSouth(c)
      case c @ Flamingos2DynamicConfig(_, _, _, _, _, _, _, _, _) =>
        InstrumentDynamicConfig.Flamingos2(c)
