// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.react.common.*
import lucuma.react.semanticui.As
import lucuma.react.semanticui.collections.form.*
import lucuma.react.semanticui.collections.grid.*
import lucuma.react.semanticui.colors.*
import lucuma.react.semanticui.elements.button.Button
import lucuma.react.semanticui.elements.icon.Icon
import lucuma.react.semanticui.floats
import lucuma.react.semanticui.modules.modal.*
import lucuma.react.semanticui.textalignment.*
import lucuma.react.semanticui.verticalalignment.*
import lucuma.react.semanticui.widths.*
import observe.model.UserDetails
import observe.web.client.actions.CloseLoginBox
import observe.web.client.actions.LoggedIn
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.components.forms.FormLabel
import observe.web.client.icons.*
import observe.web.client.model.SectionVisibilityState.*
import observe.web.client.model.*
import observe.web.client.reusability.*
import observe.web.client.services.ObserveWebClient

/**
 * UI for the login box
 */
final case class LoginBox(
  visible: SectionVisibilityState
) extends ReactProps[LoginBox](LoginBox.component)

object LoginBox {
  type Props = LoginBox

  final case class State(
    username:    String,
    password:    String,
    progressMsg: Option[String],
    errorMsg:    Option[String]
  )

  object State {
    val Empty: State = State("", "", None, None)
  }

  given Reusability[Props] = Reusability.derive[Props]
  given Reusability[State] = Reusability.derive[State]

  private val formId = "login"

  class Backend(b: BackendScope[Props, State]) {
    def pwdMod(e: ReactEventFromInput): CallbackTo[Unit] = {
      // Capture the value outside setState, react reuses the events
      val v = e.target.value
      b.modState(Focus[State](_.password).replace(v))
    }

    def userMod(e: ReactEventFromInput): CallbackTo[Unit] = {
      val v = e.target.value
      b.modState(Focus[State](_.username).replace(v))
    }

    def loggedInEvent(u: UserDetails): Callback =
      b.setState(State.Empty) >> ObserveCircuit.dispatchCB(LoggedIn(u))
    def updateProgressMsg(m: String): Callback  =
      b.modState(
        Focus[State](_.progressMsg).replace(m.some) >>> Focus[State](_.errorMsg).replace(none)
      )
    def updateErrorMsg(m: String): Callback     =
      b.modState(
        Focus[State](_.errorMsg).replace(m.some) >>> Focus[State](_.progressMsg).replace(none)
      )
    def closeBox: Callback                      =
      b.setState(State.Empty) >> ObserveCircuit.dispatchCB(CloseLoginBox)

    val attemptLogin = (e: ReactEvent, _: Form.FormProps) =>
      e.preventDefaultCB *>
        b.state >>= { s =>
        // Change the UI and call login on the remote backend
        updateProgressMsg("Authenticating...") >>
          Callback.future(
            ObserveWebClient
              .login(s.username, s.password)
              .map(loggedInEvent)
              .recover { case _: Exception =>
                updateErrorMsg("Login failed, check username/password")
              }
          )
      }

    private def toolbar(s: State): ModalActions =
      ModalActions(
        Grid(
          GridRow(verticalAlign = Middle)(
            s.progressMsg.whenDefined(m =>
              GridColumn(
                textAlign = Left,
                floated = floats.Left,
                width = Six
              )(
                IconCircleNotched.loading(true),
                m
              )
            ),
            s.errorMsg.whenDefined(m =>
              GridColumn(
                textAlign = Left,
                floated = floats.Left,
                width = Ten,
                color = Red,
                stretched = true,
                clazz = ObserveStyles.LoginError
              )(
                <.div(Icon("attention"), m)
              )
            ),
            GridColumn(
              textAlign = Right,
              floated = floats.Right,
              width = Six
            )(
              Button(onClick = closeBox)(^.tpe := "button")("Cancel"),
              Button(^.tpe := "submit")("Login")
            )
          )
        )
      )

    def render(p: Props, s: State): VdomNode =
      Modal(
        as = As.Form(
          Form(
            action = "#",
            onSubmitE = attemptLogin
          )(
            ^.id     := formId,
            ^.method := "post"
          )
        ),
        open = p.visible === SectionOpen,
        onClose = closeBox
      )(
        ModalHeader("Login"),
        ModalContent(
          <.div(
            <.div(
              ^.cls := "required field",
              FormLabel("Username", Some("username")),
              <.div(
                ^.cls := "ui icon input",
                <.input(
                  ^.`type`      := "text",
                  ^.placeholder := "Username",
                  ^.name        := "username",
                  ^.id          := "username",
                  ^.value       := s.username,
                  ^.onChange ==> userMod,
                  ^.autoFocus   := true
                ),
                IconUser
              )
            ),
            <.div(
              ^.cls := "required field",
              FormLabel("Password", Some("password")),
              <.div(
                ^.cls := "ui icon input",
                <.input(
                  ^.`type`      := "password",
                  ^.placeholder := "Password",
                  ^.name        := "password",
                  ^.id          := "password",
                  ^.value       := s.password,
                  ^.onChange ==> pwdMod
                ),
                IconLock
              )
            )
          )
        ),
        toolbar(s)
      )
  }

  val component = ScalaComponent
    .builder[Props]("Login")
    .initialState(State.Empty)
    .renderBackend[Backend]
    .configure(Reusability.shouldComponentUpdate)
    .build
}
