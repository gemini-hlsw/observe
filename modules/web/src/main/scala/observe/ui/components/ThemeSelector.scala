// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.given
import crystal.react.hooks.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.callback.CallbackCatsEffect.given
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.ui.enums.Theme
import lucuma.ui.syntax.all.*
import react.common.*
import react.primereact.*

val DefaultPendingRender: VdomNode = ProgressSpinner()

val DefaultErrorRender: Throwable => VdomNode = t =>
  Message(text = t.getMessage, severity = Message.Severity.Error)

case class ThemeSelector() extends ReactFnProps(ThemeSelector.component)

private object ThemeSelector:
  private type Props = ThemeSelector

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useState(false) // just to force rerenders
      .useEffectResultWithDepsBy((_, toggle) => toggle.value)((_, _) => _ => Theme.current)
      .render: (props, toggle, themePot) =>
        themePot.renderPot: theme =>
          ToggleButton(
            onLabel = "Light",
            offLabel = "Dark",
            checked = theme === Theme.Dark,
            onChange = value =>
              (if (value) Theme.Dark.setup[CallbackTo]
               else Theme.Light.setup[CallbackTo]) >> toggle.setState(!toggle.value)
          )
