// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import japgolly.scalajs.react.extra.router.*
import lucuma.core.enums.Instrument
import lucuma.core.util.Enumerated
import lucuma.react.common.*
import lucuma.ui.components.UnderConstruction
import observe.ui.model.Page
import observe.ui.model.Page.*
import observe.ui.model.RootModel
import japgolly.scalajs.react.ReactMonocle.*
import japgolly.scalajs.react.vdom.VdomElement
import observe.ui.components.obsList.ObsListTab
import lucuma.ui.syntax.all.*
import observe.ui.model.RootModelData
import japgolly.scalajs.react.*
import lucuma.core.model.Observation
import cats.syntax.all.given
import crystal.react.View

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
            case (LoadedInstrument(i), m) =>
              Sequence(m, i)
          })
        // | staticRoute(root / "schedule", Schedule) ~> render(UnderConstruction())
        // | staticRoute(root / "nighttime", Nighttime) ~> renderP(rootModel => Home(rootModel))
        // | staticRoute(root / "daytime", Daytime) ~> render(UnderConstruction())
        // | staticRoute(root / "excluded", Excluded) ~> render(UnderConstruction()))

      val configuration =
        rules
          .notFound(redirectToPage(Observations)(using SetRouteVia.HistoryPush))
          .renderWithP(Layout(_, _))

      configuration
