// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import diode.react.ReactPot.*
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.react.common.*
import lucuma.react.semanticui.colors.*
import lucuma.react.semanticui.elements.header.Header
import observe.web.client.icons.*
import observe.web.client.model.WebSocketConnection
import observe.web.client.reusability.*

final case class ConnectionState(u: WebSocketConnection)
    extends ReactProps[ConnectionState](ConnectionState.component)

/**
 * Alert message when the connection disappears
 */
object ConnectionState {

  type Props = ConnectionState

  given Reusability[Props] = Reusability.derive[Props]

  def formatTime(delay: Int): String =
    if (delay < 1000)
      f"${delay / 1000.0}%.1f"
    else
      f"${delay / 1000}%d"

  val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P(p =>
      Header(sub = true, clazz = ObserveStyles.item)(
        p.u.ws.renderPending(_ =>
          <.div(
            IconAttention.color(Red),
            <.span(
              ObserveStyles.errorText,
              s"Connection lost, retrying in ${formatTime(p.u.nextAttempt)} [s] ..."
            )
          )
        )
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

}
