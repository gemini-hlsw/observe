// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import scala.scalajs.js.JSConverters.*

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^._
import react.common.ReactProps
import react.semanticui.addons.confirm.Confirm
import react.semanticui.colors.*
import react.semanticui.elements.button.Button
import react.semanticui.modules.modal.ModalContent
import react.semanticui.modules.modal.ModalSize
import observe.model.UserPrompt
import observe.model.UserPrompt.*
import observe.web.client.actions.CloseUserPromptBox
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.model.*
import observe.web.client.reusability.*

final case class UserPromptBox(prompt: UserPromptState)
    extends ReactProps[UserPromptBox](UserPromptBox.component)

/**
 * UI for generic user prompts
 */
object UserPromptBox {

  def title(n: UserPrompt): String =
    n match {
      case ChecksOverride(sidName, _, _, checks) =>
        if (checks.length > 1)
          s"Warning! There are problems running sequence ${sidName.name}:"
        else
          s"Warning! There is a problem running sequence ${sidName.name}:"
    }

  def okButton(n: UserPrompt): String =
    n match {
      case _: ChecksOverride => "Stop"
    }

  def cancelButton(n: UserPrompt): String =
    n match {
      case _: ChecksOverride => "Continue anyway"
    }

  def okColor(n: UserPrompt): PromptButtonColor =
    n match {
      case _: ChecksOverride => PromptButtonColor.DefaultOk
    }

  def cancelColor(n: UserPrompt): PromptButtonColor =
    n match {
      case _: ChecksOverride => PromptButtonColor.WarningCancel
    }

  def question(n: UserPrompt): List[String] =
    n match {
      case ChecksOverride(_, _, _, checks) =>
        checks.toList.flatMap {
          case TargetCheckOverride(self)                  =>
            List("Targets in sequence and TCS do not match",
                 s"- Target in the sequence: ${self.required}, target in the TCS: ${self.actual}"
            )
          case ObsConditionsCheckOverride(cc, iq, sb, wv) =>
            List("Observing conditions do not match") ++
              List(
                cc.map(x => s"- Required Cloud Cover: ${x.required}, Actual: ${x.actual}"),
                iq.map(x => s"- Required Image Quality: ${x.required}, Actual: ${x.actual}"),
                sb.map(x => s"- Required Sky Background: ${x.required}, Actual: ${x.actual}"),
                wv.map(x => s"- Required Water Vapor: ${x.required}, Actual: ${x.actual}")
              ).collect { case Some(x) => x }
        }
    }

  type Props = UserPromptBox

  extension(c: PromptButtonColor) {
    def suiColor: Option[SemanticColor] = none
  }

  given Reusability[Props] = Reusability.by(_.prompt)

  private val ok     = Callback(ObserveCircuit.dispatch(CloseUserPromptBox(UserPromptResult.Ok)))
  private val cancel = Callback(
    ObserveCircuit.dispatch(CloseUserPromptBox(UserPromptResult.Cancel))
  )

  private val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P { p =>
      val UserPromptState(not) = p.prompt
      Confirm(
        header = not.foldMap(title),
        size = ModalSize.Tiny,
        content = ModalContent()(
          <.div(
            not
              .map(question(_).map(<.p(ObserveStyles.ConfirmLine, _)).toTagMod)
              .getOrElse(EmptyVdom)
          )
        ),
        open = not.isDefined,
        onCancel = cancel,
        onConfirm = ok,
        cancelButton = Button(content = not.foldMap(cancelButton),
                              color = not.flatMap(cancelColor(_).suiColor).orUndefined
        ),
        confirmButton = Button(content = not.foldMap(okButton),
                               color = not.flatMap(okColor(_).suiColor).orUndefined
        )
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build

}
