// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import japgolly.scalajs.react.*
import japgolly.scalajs.react.ReactMonocle.*
import japgolly.scalajs.react.extra.router.*
import lucuma.core.enums.Instrument
import lucuma.core.util.Enumerated
import lucuma.react.common.*
import observe.ui.components.obsList.ObsListTab
import observe.ui.model.Page
import observe.ui.model.Page.*
import observe.ui.model.RootModel

object Routing:

  def config: RouterWithPropsConfig[Page, RootModel] =
    RouterWithPropsConfigDsl[Page, RootModel].buildConfig: dsl =>
      import dsl.*

      val instrument: StaticDsl.RouteB[Instrument] =
        string(Enumerated[Instrument].all.map(_.tag).mkString("(", "|", ")")).pmap(s =>
          Enumerated[Instrument].fromTag(s)
        )(_.tag)

      val rules =
        (emptyRule
          | staticRoute(root, Observations) ~> renderP(p => ObsListTab(p))
          | dynamicRouteCT((root / instrument).xmapL(LoadedInstrument.iso)) ~> dynRenderP {
            case (LoadedInstrument(i), m) => SequenceTab(m, i)
          })

      val configuration =
        rules
          .notFound(redirectToPage(Observations)(using SetRouteVia.HistoryPush))
          .renderWithP(Layout(_, _))

      configuration
