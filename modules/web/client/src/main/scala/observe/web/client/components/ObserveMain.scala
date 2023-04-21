// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import cats.syntax.all.*
import diode.react.ReactPot.*
import japgolly.scalajs.react.React
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.extra.router.*
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.enums.Site
import react.common.*
import react.common.implicits.*
import react.semanticui.elements.divider.Divider
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.components.tabs.TabsArea
import observe.web.client.model.Pages.*
import observe.web.client.model.WebSocketConnection
import observe.web.client.reusability.*

final case class AppTitle(site: Site, ws: WebSocketConnection)
    extends ReactProps[AppTitle](AppTitle.component)

object AppTitle {
  type Props = AppTitle

  given Reusability[Props] = Reusability.derive[Props]

  private val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P(p =>
      Divider(as = "h4",
              horizontal = true,
              clazz = ObserveStyles.titleRow |+| ObserveStyles.notInMobile |+| ObserveStyles.header
      )(
        s"Observe ${p.site.shortName}",
        p.ws.ws.renderPending(_ =>
          <.div(
            ObserveStyles.errorText,
            ObserveStyles.blinking,
            "Connection lost"
          )
        )
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

}

final case class ObserveMain(site: Site, ctl: RouterCtl[ObservePages])
    extends ReactProps[ObserveMain](ObserveMain.component)

object ObserveMain {
  type Props = ObserveMain

  given Reusability[Props] = Reusability.by(_.site)

  private val lbConnect               = ObserveCircuit.connect(_.uiModel.loginBox)
  private val userNotificationConnect = ObserveCircuit.connect(_.uiModel.notification)
  private val userPromptConnect       = ObserveCircuit.connect(_.uiModel.userPrompt)

  private val headerSideBarConnect = ObserveCircuit.connect(ObserveCircuit.headerSideBarReader)
  private val logConnect           = ObserveCircuit.connect(_.uiModel.globalLog)
  private val wsConnect            = ObserveCircuit.connect(_.ws)

  private val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P(p =>
      React.Fragment(
        <.div(ObserveStyles.MainUI)(
          wsConnect(ws => AppTitle(p.site, ws())),
          <.div(ObserveStyles.queueAreaRow)(
            <.div(ObserveStyles.queueArea)(
              SessionQueueTableSection(p.ctl)
            ),
            <.div(ObserveStyles.headerSideBarArea)(
              headerSideBarConnect(x => HeadersSideBar(x()))
            )
          ),
          TabsArea(p.ctl, p.site),
          <.div(ObserveStyles.logArea)(
            logConnect(l => LogArea(p.site, l()))
          ),
          Footer(p.ctl, p.site)
        ),
        lbConnect(p => LoginBox(p())),
        userNotificationConnect(p => UserNotificationBox(p())),
        userPromptConnect(p => UserPromptBox(p()))
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

}
