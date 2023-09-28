// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.events.client

import cats.*
import cats.derived.*
import cats.syntax.all.*

import lucuma.core.util.Enumerated
import observe.model.Conditions
import io.circe.Encoder
import io.circe.Decoder

enum ObserveEventType(val tag: String) derives Enumerated:
  case ConditionsUpdated extends ObserveEventType("conditions_updated")

case class ObserveClientState(conditions: Conditions) derives Eq, Encoder.AsObject, Decoder

case class ObserveClientEvent(state: ObserveClientState, event: ObserveEventType)
    derives Eq,
      Encoder.AsObject,
      Decoder
