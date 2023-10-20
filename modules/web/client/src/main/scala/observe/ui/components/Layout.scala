// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.effect.IO
import cats.syntax.all.*
import crystal.react.View
import japgolly.scalajs.react.*
import japgolly.scalajs.react.extra.router.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.Css
import lucuma.react.common.ReactFnProps
import lucuma.react.common.given
import lucuma.react.primereact.Toast
import lucuma.refined.*
import lucuma.ui.components.SideTabs
import lucuma.ui.components.state.IfLogged
import lucuma.ui.hooks.*
import lucuma.ui.layout.LayoutStyles
import lucuma.ui.sso.UserVault
import observe.ui.BroadcastEvent
import observe.ui.model.AppContext
import observe.ui.model.Page
import observe.ui.model.RootModel
import observe.ui.model.RootModelData
import observe.ui.model.enums.AppTab

case class Layout(c: RouterCtl[Page], resolution: ResolutionWithProps[Page, RootModel])(
  val rootModel: RootModel
) extends ReactFnProps[Layout](Layout.component)

object Layout:
  private type Props = Layout

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useTheme()
      .render: (props, ctx, theme) =>
        import ctx.given

        val appTab: AppTab           = AppTab.from(props.resolution.page)
        val appTabView: View[AppTab] =
          View(
            appTab,
            (mod, cb) =>
              val newTab = mod(appTab)
              ctx.pushPage(newTab) >> cb(newTab)
          )

        IfLogged[BroadcastEvent](
          "Observe".refined,
          Css.Empty,
          allowGuest = false,
          ctx.ssoClient,
          props.rootModel.data.zoom(RootModelData.userVault),
          props.rootModel.data.zoom(RootModelData.userSelectionMessage),
          _ => IO.unit, // MainApp takes care of connections
          IO.unit,
          IO.unit,
          "observe".refined,
          _.event === BroadcastEvent.LogoutEventId,
          _.value.toString,
          BroadcastEvent.LogoutEvent(_)
        )(onLogout =>
          <.div(LayoutStyles.MainGrid)(
            props.rootModel.data
              .zoom(RootModelData.userVault)
              .mapValue: (userVault: View[UserVault]) =>
                props.rootModel.environment.toOption.map: environment =>
                  TopBar(environment, userVault, theme, IO.unit),
            Toast(Toast.Position.BottomRight, baseZIndex = 2000).withRef(ctx.toast.ref),
            SideTabs(
              "side-tabs".refined,
              appTabView,
              ctx.pageUrl(_),
              _ => true
            ),
            <.div(LayoutStyles.MainBody)(
              props.resolution.renderP(props.rootModel)
            )
          )
        )
