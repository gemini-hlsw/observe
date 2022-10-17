// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.given
import crystal.Pot
import crystal.react.hooks.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.callback.CallbackCatsEffect.given
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.ui.enums.Theme
import react.common.*
import react.primereact.*

val DefaultPendingRender: VdomNode = ProgressSpinner()

val DefaultErrorRender: Throwable => VdomNode = t =>
  Message(text = t.getMessage, severity = Message.Severity.Error)

  // TODO All the "potRender" methods should go in lucuma-ui, but let's unify once we settle on a component library everywhere
def potRender[A](
  valueRender:   A => VdomNode,
  pendingRender: => VdomNode = DefaultPendingRender,
  errorRender:   Throwable => VdomNode = DefaultErrorRender
): Pot[A] => VdomNode =
  _.fold(pendingRender, errorRender, valueRender)

extension [A](pot: Pot[A])
  inline def render(
    valueRender:   A => VdomNode,
    pendingRender: => VdomNode = DefaultPendingRender,
    errorRender:   Throwable => VdomNode = DefaultErrorRender
  ): VdomNode = potRender(valueRender, pendingRender, errorRender)(pot)
// TODO End move to lucuma-ui

case class ThemeSelector() extends ReactFnProps(ThemeSelector.component)

private object ThemeSelector:
  private type Props = ThemeSelector

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useState(false) // just to force rerenders
      .useEffectResultWithDepsBy((_, toggle) => toggle.value)((_, _) => _ => Theme.current)
      .render((props, toggle, themePot) =>
        themePot.render(theme =>
          ToggleButton(
            onLabel = "Light",
            offLabel = "Dark",
            checked = theme === Theme.Dark,
            onChange = value =>
              Callback.log("f") >>
                (if (value) Theme.Dark.setup[CallbackTo]
                 else Theme.Light.setup[CallbackTo]) >> toggle.setState(!toggle.value)
          )
        )
      )
