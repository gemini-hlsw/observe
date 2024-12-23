// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import japgolly.scalajs.react.extra.router.*
import lucuma.react.common.*
import lucuma.ui.components.UnderConstruction
import observe.ui.model.Page
import observe.ui.model.Page.*
import observe.ui.model.RootModel

object Routing:

  def config: RouterWithPropsConfig[Page, RootModel] =
    RouterWithPropsConfigDsl[Page, RootModel].buildConfig: dsl =>
      import dsl.*

      val rules =
        (emptyRule
          | staticRoute(root / "schedule", Schedule) ~> renderP(rootModel => UnderConstruction())
          | staticRoute(root / "nighttime", Nighttime) ~> renderP(rootModel => Home(rootModel))
          | staticRoute(root / "daytime", Daytime) ~> renderP(rootModel => UnderConstruction())
          | staticRoute(root / "excluded", Excluded) ~> renderP(rootModel => UnderConstruction()))

      val configuration =
        rules
          .notFound(redirectToPage(Nighttime)(SetRouteVia.HistoryPush))
          .renderWithP(Layout(_, _))

      configuration
