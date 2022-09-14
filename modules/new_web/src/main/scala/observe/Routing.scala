// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe

import japgolly.scalajs.react.extra.router.*
import observe.Page.*
import observe.components.Home
import react.common.*
import observe.model.RootModel
import crystal.react.View

object Routing {

  def config: RouterWithPropsConfig[Page, View[RootModel]] =
    RouterWithPropsConfigDsl[Page, View[RootModel]].buildConfig { dsl =>
      import dsl._

      val rules =
        (emptyRule
          | staticRoute(root, HomePage) ~> renderP(rootModel => Home(rootModel)))

      val configuration =
        rules
          .notFound(redirectToPage(HomePage)(SetRouteVia.HistoryPush))
          .renderWithP(Layout.apply)

      configuration
    }
}
