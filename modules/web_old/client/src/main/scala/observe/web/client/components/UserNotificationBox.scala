// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import cats.syntax.all.*
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^._
import react.common.*
import react.semanticui.colors.*
import react.semanticui.elements.button.Button
import react.semanticui.modules.modal.ModalSize
import react.semanticui.modules.modal.*
import observe.model.Notification
import observe.model.Notification.*
import observe.web.client.actions.CloseUserNotificationBox
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.icons.*
import observe.web.client.model.SectionVisibilityState.*
import observe.web.client.model.*
import observe.web.client.reusability.*

final case class UserNotificationBox(notification: UserNotificationState)
    extends ReactProps[UserNotificationBox](UserNotificationBox.component)

/**
 * UI for the model displaying resource conflicts
 */
object UserNotificationBox {
  def header(n: Notification): String =
    n match {
      case ResourceConflict(_)    => "Resource conflict"
      case InstrumentInUse(_, _)  => "Instrument busy"
      case RequestFailed(_)       => "Request failed"
      case SubsystemBusy(_, _, _) => "Resource busy"
    }

  def body(n: Notification): List[String] =
    n match {
      case ResourceConflict(obsId)     =>
        List(
          s"There is a conflict trying to run the sequence '${obsId.name}'",
          "Possibly another sequence is being executed on the same instrument"
        )
      case InstrumentInUse(obsId, ins) =>
        List(
          s"Cannot select sequence '${obsId.name}' for instrument '${ins.label}'",
          "Possibly another sequence is being executed on the same instrument"
        )
      case RequestFailed(msgs)           =>
        s"Request to the observe server failed:" :: msgs

      case SubsystemBusy(_, _, resource) =>
        List(s"Cannot configure ${resource.show}, subsystem busy")
    }

  type Props = UserNotificationBox

  given Reusability[Props] = Reusability.by(_.notification)

  private val close = Callback(ObserveCircuit.dispatch(CloseUserNotificationBox))

  private val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P { p =>
      val UserNotificationState(open, not) = p.notification
      Modal(
        size = ModalSize.Tiny,
        open = open === SectionOpen,
        onClose = close
      )(
        not.map(h => ModalHeader(header(h))),
        not.map { h =>
          ModalContent(
            <.div(body(h).toTagMod(<.p(_)))
          )
        },
        ModalActions(
          Button(color = Green, positive = true, inverted = true, onClick = close)(
            IconCheckmark,
            "Ok"
          )
        )
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build

}
