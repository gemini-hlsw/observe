// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence.steps

import cats.syntax.all.*
import crystal.react.hooks.*
import eu.timepit.refined.types.string.NonEmptyString
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.DatasetQaState
import lucuma.core.util.Enumerated
import lucuma.react.common.*
import lucuma.react.primereact.Button
import lucuma.react.primereact.InputText
import lucuma.react.primereact.OverlayPanel
import lucuma.react.primereact.SelectButtonOptional
import lucuma.react.primereact.SelectItem
import lucuma.react.primereact.hooks.all.*
import lucuma.schemas.model.Dataset
import lucuma.ui.primereact.*
import lucuma.ui.sequence.given
import lucuma.ui.syntax.render.*
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.EditableQaFields
import org.scalajs.dom.KeyCode

case class QaEditor(
  dataset:    Dataset,
  renderIcon: VdomNode,
  onChange:   Dataset.Id => EditableQaFields => Callback
) extends ReactFnProps(QaEditor.component):
  val editableQaFields: EditableQaFields = EditableQaFields.fromDataset.get(dataset)

object QaEditor:
  private type Props = QaEditor

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useStateViewBy(_.editableQaFields)
      .useOverlayPanelRef
      .render: (props, qaFields, panelRef) =>
        val submit: Callback = props.onChange(props.dataset.id)(qaFields.get)
        val reset: Callback  = qaFields.set(props.editableQaFields)

        <.span(ObserveStyles.QaStatusEditable, ^.onClick ==> panelRef.toggle)(
          props.renderIcon,
          <.span(ObserveStyles.QaStatusSelect)(Icons.ChevronDown),
          OverlayPanel(onHide = reset)(
            <.form(ObserveStyles.QaEditorPanel)(
              ^.onSubmit --> submit,
              ^.onKeyDown ==> { e =>
                e.keyCode match // Allows submitting by pressing enter after changing the status.
                  case KeyCode.Enter => submit >> panelRef.hide
                  case _             => Callback.empty
              }
            )(
              SelectButtonOptional[DatasetQaState](
                clazz = ObserveStyles.QaStatusButtonStrip,
                value = qaFields.get.qaState,
                options = Enumerated[DatasetQaState].all.map: qaState =>
                  SelectItem(qaState, qaState.tag),
                onChange = value => qaFields.mod(EditableQaFields.qaState.replace(value)),
                itemTemplate = qaState =>
                  React.Fragment(
                    qaState.value.some.renderVdom,
                    qaState.value.tag
                  )
              ).withMods(
                ^.onClick ==> ((e: ReactEvent) => e.stopPropagationCB)
              ).compact,
              InputText(
                id = "qaComment",
                value = qaFields.get.comment.map(_.value).orEmpty,
                placeholder = "Comment",
                onChange = e =>
                  qaFields.mod:
                    EditableQaFields.comment.replace(NonEmptyString.from(e.target.value).toOption)
              ).withMods(
                ^.autoComplete.off,
                ^.onClick ==> ((e: ReactEvent) => e.stopPropagationCB)
              ),
              <.div(ObserveStyles.QaEditorPanelButtons)(
                Button(icon = Icons.XMark, severity = Button.Severity.Danger).compact("Cancel"),
                Button(icon = Icons.Check, severity = Button.Severity.Success).compact
                  .withMods(^.`type` := "submit")(
                    "Save"
                  )
              )
            )
          ).withMods(ObserveStyles.QaEditorOverlay).withRef(panelRef.ref)
        )
