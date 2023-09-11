// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import crystal.react.View
import japgolly.scalajs.react.extra.router.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.*
import observe.ui.components.Home
import observe.ui.model.Page
import observe.ui.model.Page.*
import observe.ui.model.RootModel

object Routing:

  def config: RouterWithPropsConfig[Page, View[RootModel]] =
    RouterWithPropsConfigDsl[Page, View[RootModel]].buildConfig: dsl =>
      import dsl._

      val rules =
        (emptyRule
          | staticRoute(root / "schedule", Schedule) ~> renderP(rootModel => <.div("Schedule"))
          | staticRoute(root / "nighttime", Nighttime) ~> renderP(rootModel => Home(rootModel))
          | staticRoute(root / "daytime", Daytime) ~> renderP(rootModel => <.div("Daytime"))
          | staticRoute(root / "excluded", Excluded) ~> renderP(rootModel => <.div("Excluded")))

      val configuration =
        rules
          .notFound(redirectToPage(Nighttime)(SetRouteVia.HistoryPush))
          .renderWithP(Layout(_, _))

      configuration
