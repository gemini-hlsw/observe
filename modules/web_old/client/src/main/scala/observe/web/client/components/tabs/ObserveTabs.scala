// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.tabs

import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.*
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import react.common.*
import react.semanticui.collections.menu.Menu
import react.semanticui.collections.menu.MenuAttached
import react.semanticui.collections.menu.MenuTabular
import observe.model.SequenceState
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.model.AvailableTab
import observe.web.client.model.Pages.*

/**
 * Menu with tabs
 */
final case class ObserveTabs(
  router: RouterCtl[ObservePages]
) extends ReactProps[ObserveTabs](ObserveTabs.component)

object ObserveTabs {
  type Props = ObserveTabs

  given Reusability[Props] = Reusability.by(_.router)
  private val tabConnect                      = ObserveCircuit.connect(ObserveCircuit.tabsReader)

  val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P(p =>
      tabConnect { x =>
        val model                = x()
        val tabsL                = model.tabs.toList
        val runningInstruments   = tabsL.collect {
          case Right(AvailableTab(_, SequenceState.Running(_, _), i, _, _, false, _, _, _, _, _)) =>
            i
        }
        val tabs: List[VdomNode] =
          tabsL
            .sortBy {
              case Left(_)                 => Int.MinValue
              case Right(t) if t.isPreview => Int.MinValue + 1
              case Right(t)                => t.instrument.ordinal
            }
            .map {
              case Right(t) =>
                SequenceTab(p.router,
                            t,
                            model.canOperate,
                            model.displayName,
                            t.systemOverrides,
                            runningInstruments
                )
              case Left(t)  =>
                CalibrationQueueTab(p.router, t)
            }
        React.Fragment(
          Menu(tabular = MenuTabular.Tabular, attached = MenuAttached.Attached)(
            tabs: _*
          )
        )
      }
    )
    .configure(Reusability.shouldComponentUpdate)
    .build
}
