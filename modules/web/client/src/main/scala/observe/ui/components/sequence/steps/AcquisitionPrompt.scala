// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.*
import lucuma.react.fa.IconSize
import lucuma.react.primereact.Button
import lucuma.ui.primereact.*
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.enums.OperationRequest
import crystal.react.View

case class AcquisitionPrompt(
  onProceed:        Callback,
  onRepeat:         Callback,
  operationRequest: OperationRequest,
  clicked:          View[Boolean]
) extends ReactFnProps(AcquisitionPrompt.component)

object AcquisitionPrompt:
  private type Props = AcquisitionPrompt

  // TODO REMOVE ISINFLIGHT!

  private val component = ScalaFnComponent[Props]: props =>
    <.div(ObserveStyles.AcquisitionPrompt)(
      Icons.CircleQuestion.withSize(IconSize.LG),
      <.div(ObserveStyles.AcquisitionPromptMain)(
        <.div("Has the target been acquired?"),
        <.div(
          Button(
            size = Button.Size.Small,
            icon = Icons.CircleCheck,
            label = "Yes, start observation",
            disabled = props.clicked.get,
            onClick = props.onProceed >> props.clicked.set(true)
          ).compact,
          Button(
            size = Button.Size.Small,
            icon = Icons.ArrowsRetweet,
            label = "No, take another step",
            disabled = props.clicked.get,
            onClick = props.onRepeat >> props.clicked.set(true)
          ).compact
        )
      ),
      <.div(ObserveStyles.AcquisitionPromptBusy)(Icons.CircleNotch.withSize(IconSize.XL))
        .when(props.clicked.get)
    )
