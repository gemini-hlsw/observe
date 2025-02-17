// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.effect.IO
import crystal.react.*
import crystal.react.View
import crystal.react.hooks.*
import eu.timepit.refined.types.string.NonEmptyString
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
import observe.model.ClientConfig
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.AppContext

case class TopBar(
  clientConfig: ClientConfig,
  vault:        View[UserVault],
  theme:        View[Theme],
  onLogout:     IO[Unit]
) extends ReactFnProps(TopBar)

object TopBar
    extends ReactFnComponent[TopBar](props =>
      object IsAboutOpen extends NewType[Boolean]

      type ForceRerender = ForceRerender.Type
      object ForceRerender extends NewType[Boolean]:
        extension (s: ForceRerender)
          def flip: ForceRerender =
            if (s.value) ForceRerender(true) else ForceRerender(false)

      for
        ctx         <- useContext(AppContext.ctx)
        isAboutOpen <- useStateView(IsAboutOpen(false))
        menuRef     <- usePopupMenuRef
      yield
        import ctx.given

        val user = props.vault.get.user

        def logout: IO[Unit] = ctx.ssoClient.logout >> props.onLogout

        val firstItems = List(
          MenuItem.Item(
            label = "About Observe",
            icon = Icons.CircleInfo,
            command = isAboutOpen.set(IsAboutOpen(true))
          ),
          MenuItem.Item(label = "Logout", icon = Icons.Logout, command = logout.runAsync)
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
              <.span(props.clientConfig.site.shortName),
              " - ",
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
              NonEmptyString
                .unsafeFrom(
                  s"${ctx.version.value} / Server: ${props.clientConfig.version.value.value}"
                ),
              isAboutOpen.as(IsAboutOpen.value)
            )
          else
            EmptyVdom,
        )
    )
