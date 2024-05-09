// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import lucuma.react.common.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.primereact.Button
import observe.ui.ObserveStyles
import observe.ui.Icons
import lucuma.react.fa.IconSize
import lucuma.ui.primereact.*
import observe.ui.model.enums.OperationRequest

case class AcquisitionPrompt(
  onProceed:        Callback,
  onRepeat:         Callback,
  operationRequest: OperationRequest
) extends ReactFnProps(AcquisitionPrompt.component)

object AcquisitionPrompt:
  private type Props = AcquisitionPrompt

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
            disabled = props.operationRequest.isInFlight,
            onClick = props.onProceed
          ).compact,
          Button(
            size = Button.Size.Small,
            icon = Icons.ArrowsRetweet,
            label = "No, take another step",
            disabled = props.operationRequest.isInFlight,
            onClick = props.onRepeat
          ).compact
        )
      )
    )
