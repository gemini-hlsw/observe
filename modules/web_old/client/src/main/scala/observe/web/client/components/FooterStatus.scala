// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import cats.syntax.all.*
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^._
import react.common.*
import react.common.implicits.*
import react.semanticui.elements.header.Header
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.model.ClientStatus
import observe.web.client.reusability.*

final case class FooterStatus(status: ClientStatus)
    extends ReactProps[FooterStatus](FooterStatus.component)

/**
 * Chooses to display either the guide config or a connection status info
 */
object FooterStatus {

  type Props = FooterStatus

  given Reusability[Props] = Reusability.derive[Props]
  private val wsConnect                       = ObserveCircuit.connect(_.ws)
  private val gcConnect                       = ObserveCircuit.connect(_.guideConfig)

  private val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P(p =>
      React.Fragment(
        Header(sub = true, clazz = ObserveStyles.item |+| ObserveStyles.notInMobile)(
          wsConnect(x => ConnectionState(x())).unless(p.status.isConnected)
        ),
        Header(sub = true, clazz = ObserveStyles.item |+| ObserveStyles.notInMobile)(
          gcConnect(x => GuideConfigStatus(x())).when(p.status.isConnected)
        ),
        ControlMenu(p.status)
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

}
