// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.events

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import observe.model.ClientId
import observe.model.events.ClientEvent

case class TargetedClientEvent protected (clientId: Option[ClientId], event: ClientEvent) derives Eq

object TargetedClientEvent:
  def forAllClients(event: ClientEvent.AllClientEvent): TargetedClientEvent =
    TargetedClientEvent(none, event)

  def forSingleClient(
    clientId: ClientId,
    event:    ClientEvent.SingleClientEvent
  ): TargetedClientEvent =
    TargetedClientEvent(clientId.some, event)

  given Conversion[ClientEvent.AllClientEvent, TargetedClientEvent]             = forAllClients
  given Conversion[List[ClientEvent.AllClientEvent], List[TargetedClientEvent]] =
    _.map(forAllClients)

extension (event: ClientEvent.SingleClientEvent)
  def forClient(clientId: ClientId): TargetedClientEvent =
    TargetedClientEvent.forSingleClient(clientId, event)
