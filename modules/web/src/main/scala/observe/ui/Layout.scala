// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import cats.effect.IO
import cats.syntax.all.*
import crystal.react.View
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router._
import lucuma.refined.*
import lucuma.ui.components.state.IfLogged
import lucuma.ui.sso.UserVault
import observe.ui.model.RootModel
import lucuma.react.common.Css
import lucuma.react.common.ReactFnProps
import lucuma.react.common.given

case class Layout(c: RouterCtl[Page], resolution: ResolutionWithProps[Page, View[RootModel]])(
  val rootModel: View[RootModel]
) extends ReactFnProps[Layout](Layout.component)

object Layout:
  private type Props = Layout

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .render: (props, ctx) =>
        import ctx.given

        IfLogged[BroadcastEvent](
          "Observe".refined,
          Css.Empty,
          allowGuest = false,
          ctx.ssoClient,
          props.rootModel.zoom(RootModel.userVault),
          props.rootModel.zoom(RootModel.userSelectionMessage),
          ctx.initODBClient(_),
          ctx.closeODBClient,
          IO.unit,
          "observe".refined,
          _.event === BroadcastEvent.LogoutEventId,
          _.value.toString,
          BroadcastEvent.LogoutEvent(_)
        )((vault: UserVault, onLogout: IO[Unit]) => props.resolution.renderP(props.rootModel))
