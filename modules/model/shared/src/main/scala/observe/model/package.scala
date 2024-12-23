// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.*
import cats.syntax.all.*
import io.circe.KeyDecoder
import io.circe.KeyEncoder
import lucuma.core.enums.Instrument
import lucuma.core.enums.Site
import lucuma.core.util.Enumerated
import observe.model.enums.Resource

import java.util.UUID

type ObservationName = String
type TargetName      = String

val UnknownTargetName = "None"

val CalibrationQueueName: String = "Calibration Queue"
val CalibrationQueueId: QueueId  =
  QueueId(UUID.fromString("7156fa7e-48a6-49d1-a267-dbf3bbaa7577"))

extension (i: Instrument)
  def hasOI: Boolean = i match
//      case Instrument.F2    => true
    case Instrument.GmosSouth => true
    case Instrument.GmosNorth => true
    case _                    => false
//      case Instrument.Nifs  => true
//      case Instrument.Niri  => true
//      case Instrument.Gnirs => true
//      case Instrument.Gsaoi => false
//      case Instrument.Gpi   => true
//      case Instrument.Ghost => false

  def site: Option[Site] = i match
    // GS
    case Instrument.GmosSouth  => Site.GS.some
    case Instrument.Flamingos2 => Site.GS.some
    case Instrument.Ghost      => Site.GS.some
    case Instrument.Gpi        => Site.GS.some
    case Instrument.Gsaoi      => Site.GS.some
    // GN
    case Instrument.GmosNorth  => Site.GN.some
    case Instrument.Gnirs      => Site.GN.some
    case Instrument.Niri       => Site.GN.some
    case Instrument.Nifs       => Site.GN.some
    // None
    case _                     => none

given KeyEncoder[Resource | Instrument] = KeyEncoder.instance:
  case r: Resource   => r.tag
  case i: Instrument => i.tag
given KeyDecoder[Resource | Instrument] = KeyDecoder.instance: tag =>
  Enumerated[Resource].fromTag(tag).orElse(Enumerated[Instrument].fromTag(tag))

// Resources come before Instruments
given Enumerated[Resource | Instrument] = Enumerated
  .from(
    Enumerated[Resource].all.head,
    (Enumerated[Resource].all.tail ++ Enumerated[Instrument].all)*
  )
  .withTag:
    case r: Resource   => r.tag
    case i: Instrument => i.tag
