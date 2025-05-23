// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.effect.IO
import cats.syntax.all.*
import clue.PersistentClientStatus
import crystal.Pot
import crystal.react.*
import crystal.react.hooks.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.extra.router.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.*
import lucuma.react.primereact.Toast
import lucuma.refined.*
import lucuma.ui.components.SideTabs
import lucuma.ui.components.SolarProgress
import lucuma.ui.enums.Theme
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
) extends ReactFnProps(Layout)

object Layout
    extends ReactFnComponent[Layout](props =>
      for
        ctx       <- useContext(AppContext.ctx)
        odbStatus <- useStreamOnMount(ctx.odbClient.statusStream)
        theme     <- useTheme(initial = Theme.Dark)
      yield
        val appTab: AppTab           = AppTab.from(props.resolution.page)
        val appTabView: View[AppTab] =
          View(
            appTab,
            (mod, cb) =>
              val newTab = mod(appTab)
              ctx.pushPage(newTab) >> cb(appTab, newTab)
          )

        if (
          odbStatus.contains_(PersistentClientStatus.Connected) &&
          props.rootModel.clientConfig.isReady
        )
          React.StrictMode(
            <.div(LayoutStyles.MainGrid)(
              props.rootModel.data
                .zoom(RootModelData.userVault)
                .zoom(Pot.readyPrism.some)
                .mapValue: (userVault: View[UserVault]) =>
                  props.rootModel.clientConfig.toOption.map: clientConfig =>
                    TopBar(
                      clientConfig,
                      userVault,
                      theme,
                      props.rootModel.data.zoom(RootModelData.isAudioActivated),
                      props.rootModel.data.zoom(RootModelData.userVault).set(Pot(none)).toAsync
                    ),
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
        else
          SolarProgress()
    )
