// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.components

import react.common.ReactFnProps
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import reactST.primereact.components.*
import crystal.react.hooks.*
import observe.Theme
import crystal.Pot
import reactST.primereact.messageMod.MessageSeverityType
import cats.syntax.all.given
import japgolly.scalajs.react.callback.CallbackCatsEffect.given

// TODO All the "potRender" methods should go in lucuma-ui
val DefaultPendingRender: VdomNode = ProgressSpinner()

val DefaultErrorRender: Throwable => VdomNode =
  t => Message().text(t.getMessage).severity(MessageSeverityType.error)


def potRender[A](
  valueRender:   A => VdomNode,
  pendingRender: => VdomNode = DefaultPendingRender,
  errorRender:   Throwable => VdomNode = DefaultErrorRender
): Pot[A] => VdomNode =
  _.fold(pendingRender, errorRender, valueRender)



final implicit class PotRenderOps[A](val pot: Pot[A]) extends AnyVal {
  inline def render(
    valueRender:   A => VdomNode,
    pendingRender: => VdomNode = DefaultPendingRender,
    errorRender:   Throwable => VdomNode = DefaultErrorRender
  ): VdomNode = potRender(valueRender, pendingRender, errorRender)(pot)
}

final case class ThemeSelector() extends ReactFnProps(ThemeSelector.component)

object ThemeSelector:
  type Props = ThemeSelector

  val component = ScalaFnComponent
    .withHooks[Props]
    .useState(false) // just to force rerenders
    .useEffectResultWithDepsBy( (_, toggle) => toggle.value)( (_, _) => _ => Theme.current)
    .render( (props, toggle, themePot) => 
      themePot.render(theme =>
        ToggleButton()
          .onLabel("Light")
          .offLabel("Dark")
          .checked(theme === Theme.Dark)
          .onChange( e => (if(e.value) Theme.Dark.setup[CallbackTo] else Theme.Light.setup[CallbackTo]) >> toggle.setState(!toggle.value))
      )
    )
