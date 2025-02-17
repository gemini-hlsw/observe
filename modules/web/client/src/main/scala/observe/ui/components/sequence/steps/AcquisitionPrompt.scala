// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import crystal.react.View
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.SequenceType
import lucuma.react.common.*
import lucuma.react.fa.IconSize
import lucuma.react.primereact.Button
import lucuma.ui.LucumaIcons
import lucuma.ui.primereact.*
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.enums.OperationRequest

case class AcquisitionPrompt(
  onProceed:        Callback,
  onRepeat:         Callback,
  operationRequest: OperationRequest,
  clicked:          View[Option[SequenceType]]
) extends ReactFnProps(AcquisitionPrompt)

object AcquisitionPrompt
    extends ReactFnComponent[AcquisitionPrompt](props =>
      // TODO REMOVE ISINFLIGHT!

      <.div(ObserveStyles.AcquisitionPrompt)(
        Icons.CircleQuestion.withSize(IconSize.LG),
        <.div(ObserveStyles.AcquisitionPromptMain)(
          <.div("Has the target been acquired?"),
          <.div(
            Button(
              size = Button.Size.Small,
              icon = props.clicked.get match
                case Some(SequenceType.Science) => LucumaIcons.CircleNotch
                case _                          => Icons.CircleCheck,
              label = "Yes, start observation",
              disabled = props.clicked.get.isDefined,
              severity = props.clicked.get match
                case Some(SequenceType.Acquisition) => Button.Severity.Secondary
                case _                              => Button.Severity.Primary,
              onClick = props.onProceed >> props.clicked.set(SequenceType.Science.some)
            ).compact,
            Button(
              size = Button.Size.Small,
              icon = props.clicked.get match
                case Some(SequenceType.Acquisition) => LucumaIcons.CircleNotch
                case _                              => Icons.ArrowsRetweet,
              label = "No, take another step",
              disabled = props.clicked.get.isDefined,
              severity = props.clicked.get match
                case Some(SequenceType.Science) => Button.Severity.Secondary
                case _                          => Button.Severity.Primary,
              onClick = props.onRepeat >> props.clicked.set(SequenceType.Acquisition.some)
            ).compact
          )
        ),
        <.div(ObserveStyles.AcquisitionPromptBusy)(LucumaIcons.CircleNotch.withSize(IconSize.XL))
          .when(props.clicked.get.isDefined)
      )
    )
