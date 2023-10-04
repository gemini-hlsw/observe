// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.effect.IO
import crystal.react.View
import crystal.react.*
import crystal.react.hooks.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.util.NewType
import lucuma.react.common.*
import lucuma.react.fa.FontAwesomeIcon
import lucuma.react.primereact.Button
import lucuma.react.primereact.MenuItem
import lucuma.react.primereact.PopupTieredMenu
import lucuma.react.primereact.Toolbar
import lucuma.react.primereact.hooks.all.*
import lucuma.refined.*
import lucuma.ui.components.About
import lucuma.ui.components.ThemeSubMenu
import lucuma.ui.enums.Theme
import lucuma.ui.layout.LayoutStyles
import lucuma.ui.sso.UserVault
import lucuma.ui.syntax.all.given
import observe.ui.model.AppContext
import observe.ui.Icons
import observe.ui.ObserveStyles

case class TopBar(
  vault:    View[UserVault],
  theme:    View[Theme],
  onLogout: IO[Unit]
) extends ReactFnProps(TopBar.component)

object TopBar:
  private type Props = TopBar

  private object IsAboutOpen extends NewType[Boolean]

  private type ForceRerender = ForceRerender.Type
  private object ForceRerender extends NewType[Boolean]:
    extension (s: ForceRerender)
      def flip: ForceRerender =
        if (s.value) ForceRerender(true) else ForceRerender(false)

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useStateView(IsAboutOpen(false))
      .usePopupMenuRef
      .render: (props, ctx, isAboutOpen, menuRef) =>
        val user = props.vault.get.user

        // TODO Logout option
        // def logout: IO[Unit] = ctx.ssoClient.logout >> props.onLogout

        val firstItems = List(
          MenuItem.Item(
            label = "About Observe",
            icon = Icons.CircleInfo,
            command = isAboutOpen.set(IsAboutOpen(true))
          )
        )

        val lastItems = List(
          MenuItem.Separator,
          ThemeSubMenu(props.theme)
        )

        val menuItems = firstItems ::: lastItems

        React.Fragment(
          Toolbar(
            clazz = LayoutStyles.MainHeader,
            left = <.span(LayoutStyles.MainTitle, "Observe"),
            right = React.Fragment(
              <.span(LayoutStyles.MainUserName)(user.displayName),
              Button(
                icon = Icons.Bars,
                text = true,
                severity = Button.Severity.Secondary,
                onClickE = menuRef.toggle
              )
            )
          ),
          PopupTieredMenu(model = menuItems).withRef(menuRef.ref),
          if (isAboutOpen.get.value)
            About(
              "Observe".refined,
              ObserveStyles.LoginTitle,
              ctx.version,
              isAboutOpen.as(IsAboutOpen.value)
            )
          else
            EmptyVdom,
        )
