// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import observe.AppContext
import react.common.ReactFnProps

final case class Home() extends ReactFnProps[Home](Home.component)

object Home {
  protected type Props = Home

  protected val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useEffectBy { (_, ctx) =>
        ctx.logger.debug("Rendering Home component") // Running an IO in useEffect
      }
      .useState(0)
      .render { (_, _, clicks) =>
        <.div(
          <.p("HELLO WORLD!"),
          <.p(
            s"You clicked ${clicks.value} time(s).",
            <.button("Click me!", ^.onClick --> clicks.modState(_ + 1))
          )
        )
      }
}
