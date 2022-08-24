// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe

import japgolly.scalajs.react.extra.router._
import observe.Page._
import observe.components.Home
import react.common._

object Routing {

  def config: RouterConfig[Page] =
    RouterConfigDsl[Page].buildConfig { dsl =>
      import dsl._

      val rules =
        (emptyRule
          | staticRoute(root, HomePage) ~> renderP(_ => Home()))

      val configuration =
        rules
          .notFound(redirectToPage(HomePage)(SetRouteVia.HistoryPush))
          .renderWith(Layout.apply)

      configuration
    }
}
