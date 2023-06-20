// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import crystal.react.View
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._
import observe.ui.model.RootModel
import react.common.ReactFnProps

case class Layout(c: RouterCtl[Page], resolution: ResolutionWithProps[Page, View[RootModel]])(
  val rootModel: View[RootModel]
) extends ReactFnProps[Layout](Layout.component)

object Layout:
  protected type Props = Layout

  protected val component = ScalaFnComponent[Props] { props =>
    props.resolution.renderP(props.rootModel)
  }
