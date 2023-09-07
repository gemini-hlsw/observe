// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.effect.IO
import cats.syntax.all.*
import crystal.react.View
import crystal.react.*
import crystal.react.hooks.*
// import explore.components.ConnectionsStatus
// import explore.components.ui.ExploreStyles
// import explore.model.AppContext
// import explore.model.ExploreLocalPreferences
// import explore.model.ExploreLocalPreferences.*
// import explore.model.ProgramInfoList
// import explore.model.ProgramSummaries
// import explore.programs.ProgramsPopup
// import explore.undo.UndoStacks
// import explore.users.UserPreferencesPopup
import japgolly.scalajs.react.*
import japgolly.scalajs.react.callback.CallbackCatsEffect.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.GuestRole
import lucuma.core.model.Program
import lucuma.core.util.NewType
import lucuma.react.common.*
import lucuma.react.fa.FontAwesomeIcon
import lucuma.react.primereact.Button
import lucuma.react.primereact.Image
import lucuma.react.primereact.MenuItem
import lucuma.react.primereact.PopupTieredMenu
import lucuma.react.primereact.Toolbar
import lucuma.react.primereact.hooks.all.*
import lucuma.refined.*
import lucuma.ui.Resources
import lucuma.ui.components.About
import lucuma.ui.components.LoginStyles
import lucuma.ui.enums.ExecutionEnvironment
import lucuma.ui.enums.Theme
import lucuma.ui.sso.UserVault
import lucuma.ui.syntax.all.given
import org.scalajs.dom
import org.scalajs.dom.window
import observe.ui.AppContext
import observe.ui.Icons
import lucuma.ui.components.ThemeSubMenu

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
        import ctx.given

        val user = props.vault.get.user
        val role = user.role

        def logout: IO[Unit] = ctx.ssoClient.logout >> props.onLogout

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
          // Toolbar(
          //   clazz = ExploreStyles.MainHeader,
          //   left = <.span(ExploreStyles.MainTitle, "Explore"),
          //   right = React.Fragment(
          //     RoleSwitch(props.vault),
          //     ConnectionsStatus(),
          //     Button(
          //       icon = Icons.Bars,
          //       text = true,
          //       severity = Button.Severity.Secondary,
          //       onClickE = menuRef.toggle
          //     )
          //   )
          // ),
          PopupTieredMenu(model = menuItems).withRef(menuRef.ref),
          if (isAboutOpen.get.value)
            About(
              "Observe".refined,
              // ExploreStyles.LoginTitle,
              Css.Empty,
              ctx.version,
              isAboutOpen.as(IsAboutOpen.value)
            )
          else
            EmptyVdom,
        )
