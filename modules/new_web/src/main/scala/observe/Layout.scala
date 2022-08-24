// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._
import react.common.ReactFnProps

final case class Layout(c: RouterCtl[Page], r: Resolution[Page])
    extends ReactFnProps[Layout](Layout.component)

object Layout {
  protected type Props = Layout

  protected val component = ScalaFnComponent[Props] { props =>
    <.div(^.cls  := "app")(
      <.h2(^.cls := "topbar", "Observe"),
      <.div(props.r.render())
    )
  }
}
