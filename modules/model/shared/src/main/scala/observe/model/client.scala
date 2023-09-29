// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.events.client

import cats.*
import cats.derived.*
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.Encoder
import io.circe.syntax.*
import lucuma.core.enums.Site
import lucuma.core.util.Enumerated
import observe.model.ClientId
import observe.model.Conditions

enum ObserveEventType(val tag: String) derives Enumerated:
  case StateRefresh      extends ObserveEventType("state_refreshed")
  case ConditionsUpdated extends ObserveEventType("conditions_updated")

sealed trait ClientEvent derives Eq

object ClientEvent:
  case class InitialEvent(site: Site, clientId: ClientId, version: String) extends ClientEvent
      derives Eq,
        Encoder.AsObject,
        Decoder

  case class ObserveState(conditions: Conditions) extends ClientEvent
      derives Eq,
        Encoder.AsObject,
        Decoder

  given Encoder[ClientEvent] = Encoder.instance:
    case e @ InitialEvent(_, _, _) => e.asJson
    case e @ ObserveState(_)       => e.asJson

  given Decoder[ClientEvent] =
    List[Decoder[ClientEvent]](
      Decoder[InitialEvent].widen,
      Decoder[ObserveState].widen
    ).reduceLeft(_ or _)
