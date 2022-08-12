// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.enums.Site
import react.clipboard.CopyToClipboard
import react.common._
import react.semanticui.collections.menu._
import react.semanticui.modules.popup.Popup
import react.semanticui.modules.popup.PopupPosition
import react.semanticui.sizes._
import observe.web.client.OcsBuildInfo
import observe.web.client.actions.SelectCalibrationQueue
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.model.Pages._
import observe.web.client.reusability._

final case class Footer(router: RouterCtl[ObservePages], site: Site)
    extends ReactProps[Footer](Footer.component)

/**
 * Component for the bar at the top of the page
 */
object Footer {
  type Props = Footer

  implicit val propsReuse: Reusability[Props] = Reusability.by(_.site)

  private val userConnect = ObserveCircuit.connect(ObserveCircuit.statusReader)

  private def goHome(p: Props)(e: ReactEvent): Callback =
    e.preventDefaultCB *>
      p.router.dispatchAndSetUrlCB(SelectCalibrationQueue)

  private val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P(p =>
      Menu(
        clazz = ObserveStyles.Footer,
        inverted = true
      )(
        MenuItem(
          as = "a",
          header = true,
          clazz = ObserveStyles.notInMobile,
          onClickE = goHome(p) _
        )(s"Observe - ${p.site.shortName}"),
        Popup(
          position = PopupPosition.TopCenter,
          size = Tiny,
          trigger = MenuItem(as = <.a, header = true, clazz = ObserveStyles.notInMobile)(
            CopyToClipboard(text = OcsBuildInfo.version)(
              OcsBuildInfo.version
            )
          )
        )(
          "Copy to clipboard"
        ),
        userConnect(x => FooterStatus(x()))
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

}
