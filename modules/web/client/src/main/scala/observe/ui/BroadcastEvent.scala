// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import fs2.dom.Serializer

import scala.scalajs.js

// These are messages sent across tabs thus they need to be JS compatible
// We don't need yet more than just an index to differentiate
sealed trait BroadcastEvent extends js.Object:
  def event: Int
  def value: js.Any // encode whatever value as a String. it can be e.g. json

object BroadcastEvent:
  given Serializer[BroadcastEvent] =
    Serializer.any.imap(_.asInstanceOf[BroadcastEvent])(identity(_))

  val LogoutEventId = 1

  class LogoutEvent(val nonce: String) extends BroadcastEvent:
    val event = LogoutEventId
    val value = nonce
