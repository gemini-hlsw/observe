// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.effect.IO
import cats.syntax.all.*
import clue.PersistentClientStatus
import crystal.Pot
import crystal.react.View
import crystal.react.hooks.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.extra.router.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.ReactFnProps
import lucuma.react.common.given
import lucuma.react.primereact.Toast
import lucuma.refined.*
import lucuma.ui.components.SideTabs
import lucuma.ui.hooks.*
import lucuma.ui.layout.LayoutStyles
import lucuma.ui.sso.UserVault
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
      .useStreamOnMountBy((_, ctx) => ctx.odbClient.statusStream)
      .useTheme()
      .render: (props, ctx, odbStatus, theme) =>
        val appTab: AppTab           = AppTab.from(props.resolution.page)
        val appTabView: View[AppTab] =
          View(
            appTab,
            (mod, cb) =>
              val newTab = mod(appTab)
              ctx.pushPage(newTab) >> cb(newTab)
          )

        if (
          odbStatus.contains_(
            PersistentClientStatus.Initialized
          ) && props.rootModel.clientConfig.isReady
        )
          <.div(LayoutStyles.MainGrid)(
            props.rootModel.data
              .zoom(RootModelData.userVault)
              .zoom(Pot.readyPrism.some)
              .mapValue: (userVault: View[UserVault]) =>
                props.rootModel.clientConfig.toOption.map: clientConfig =>
                  TopBar(clientConfig, userVault, theme, IO.unit),
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
        else
          EmptyVdom
