// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.tabs

import japgolly.scalajs.react.React
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.enums.Site
import react.common._
import observe.web.client.circuit._
import observe.web.client.components.queue.CalQueueTabContent
import observe.web.client.model.Pages.ObservePages
import observe.web.client.reusability._

/**
 * Top level container of the tabs area
 */
final case class TabsArea(router: RouterCtl[ObservePages], site: Site)
    extends ReactProps[TabsArea](TabsArea.component)

object TabsArea {
  type Props = TabsArea

  implicit val propsReuse: Reusability[Props] = Reusability.by(_.site)
  private val tabsConnect                     = ObserveCircuit.connect(ObserveCircuit.observeTabs)

  val component = ScalaComponent
    .builder[Props]("TabsArea")
    .stateless
    .render_P(p =>
      React.Fragment(
        ObserveTabs(p.router),
        tabsConnect(x =>
          React
            .Fragment(
              x().toList.collect[VdomNode] {
                case t: SequenceTabContentFocus if t.isActive =>
                  SequenceTabContent(p.router, t)
                case t if t.isActive                          =>
                  CalQueueTabContent(t.canOperate, t.active, t.logDisplayed)
              }: _*
            )
        )
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build
}
