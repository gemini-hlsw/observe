// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.toolbars

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import cats.syntax.all._
import japgolly.scalajs.react.AsyncCallback
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.component.builder.Lifecycle.RenderScope
import japgolly.scalajs.react.vdom.html_<^._
import mouse.all._
import react.common._
import react.semanticui.collections.form.FormCheckbox
import react.semanticui.colors._
import react.semanticui.elements.button.Button
import react.semanticui.modules.popup.Popup
import observe.model.{ Observation, SystemOverrides }
import observe.web.client.actions._
import observe.web.client.actions.RunOptions
import observe.web.client.circuit._
import observe.web.client.components.ObserveStyles
import observe.web.client.icons._
import observe.web.client.model.CancelPauseOperation
import observe.web.client.model.PauseOperation
import observe.web.client.model.RunOperation
import observe.web.client.model.SectionVisibilityState
import observe.web.client.model.SyncOperation
import observe.web.client.reusability._
import observe.web.client.semanticui.controlButton
import observe.web.client.services.ObserveWebClient

final case class SequenceControl(p: SequenceControlFocus)
    extends ReactProps[SequenceControl](SequenceControl.component) {
  private val runRequested: RunOperation =
    p.control.tabOperations.runRequested

  private val syncRequested: SyncOperation =
    p.control.tabOperations.syncRequested

  private val pauseRequested: PauseOperation =
    p.control.tabOperations.pauseRequested

  private val cancelPauseRequested: CancelPauseOperation =
    p.control.tabOperations.cancelPauseRequested

  private val syncIdle: Boolean        =
    syncRequested === SyncOperation.SyncIdle
  private val runIdle: Boolean         =
    runRequested === RunOperation.RunIdle
  private val pauseIdle: Boolean       =
    pauseRequested === PauseOperation.PauseIdle
  private val cancelPauseIdle: Boolean =
    cancelPauseRequested === CancelPauseOperation.CancelPauseIdle

  val canSync: Boolean        =
    p.canOperate && syncIdle && runIdle
  val canRun: Boolean         =
    p.canOperate && runIdle && syncIdle && !p.control.tabOperations.anyResourceInFlight
  val canPause: Boolean       = p.canOperate && pauseIdle
  val canCancelPause: Boolean = p.canOperate && cancelPauseIdle

  val overrideControlsOpen: Boolean =
    p.overrideSubsysControls === SectionVisibilityState.SectionOpen
}

/**
 * Control buttons for the sequence
 */
object SequenceControl {
  type Props = SequenceControl

  implicit val propsReuse: Reusability[Props] = Reusability.derive[Props]

  def requestRun(s: Observation.Id): Callback =
    ObserveCircuit.dispatchCB(RequestRun(s, RunOptions.Normal))

  def requestSync(idName: Observation.IdName): Callback =
    ObserveCircuit.dispatchCB(RequestSync(idName))

  def requestPause(idName: Observation.IdName): Callback =
    ObserveCircuit.dispatchCB(RequestPause(idName))

  def requestCancelPause(id: Observation.Id): Callback =
    ObserveCircuit.dispatchCB(RequestCancelPause(id))

  private def syncButton(idName: Observation.IdName, canSync: Boolean) =
    controlButton(icon = IconRefresh,
                  color = Purple,
                  onClick = requestSync(idName),
                  disabled = !canSync,
                  tooltip = "Sync sequence",
                  text = "Sync"
    )

  private def runButton(
    id:                  Observation.Id,
    isPartiallyExecuted: Boolean,
    nextStepToRun:       Int,
    canRun:              Boolean
  ) = {
    val runContinueTooltip =
      s"${isPartiallyExecuted.fold("Continue", "Run")} the sequence from the step $nextStepToRun"
    val runContinueButton  =
      s"${isPartiallyExecuted.fold("Continue", "Run")} from step $nextStepToRun"
    controlButton(icon = IconPlay,
                  color = Blue,
                  onClick = requestRun(id),
                  disabled = !canRun,
                  tooltip = runContinueTooltip,
                  text = runContinueButton
    )
  }

  private def cancelPauseButton(id: Observation.Id, canCancelPause: Boolean) =
    controlButton(
      icon = IconBan,
      color = Brown,
      onClick = requestCancelPause(id),
      disabled = !canCancelPause,
      tooltip = "Cancel process to pause the sequence",
      text = "Cancel Pause"
    )

  private def pauseButton(idName: Observation.IdName, canPause: Boolean) =
    controlButton(
      icon = IconPause,
      color = Teal,
      onClick = requestPause(idName),
      disabled = !canPause,
      tooltip = "Pause the sequence after the current step completes",
      text = "Pause"
    )

  private def subsystemsButton($ : RenderScope[Props, Unit, Unit], overrides: SystemOverrides) = {

    def subsystemCheck(
      label:   String,
      checked: => Boolean,
      cb:      (Observation.Id, Boolean) => Future[Unit]
    ) =
      FormCheckbox(
        label = label,
        checked = checked,
        disabled = ! $.props.canRun,
        onClick = AsyncCallback.fromFuture(cb($.props.p.obsId, !checked)).toCallback
      ).when($.props.overrideControlsOpen)

    <.div(
      ObserveStyles.SubsystemsForm,
      Popup(
        content = "Enable/Disable subsystems",
        trigger = Button(
          icon = true,
          active = $.props.p.overrideSubsysControls === SectionVisibilityState.SectionOpen,
          toggle = true,
          onClick = ObserveCircuit.dispatchCB(
            FlipSubystemsControls($.props.p.obsId, $.props.p.overrideSubsysControls.flip)
          )
        )(IconTools)
      ),
      subsystemCheck("TCS", overrides.isTcsEnabled, ObserveWebClient.toggleTCS),
      subsystemCheck("GCAL", overrides.isGcalEnabled, ObserveWebClient.toggleGCAL),
      subsystemCheck("DHS", overrides.isDhsEnabled, ObserveWebClient.toggleDHS),
      subsystemCheck($.props.p.instrument.show,
                     overrides.isInstrumentEnabled,
                     ObserveWebClient.toggleInstrument
      )
    )
  }

  private def component =
    ScalaComponent
      .builder[Props]
      .renderP { ($, p) =>
        val SequenceControlFocus(_, _, overrides, _, _, control)  = p.p
        val ControlModel(idName, partial, nextStepIdx, status, _) = control
        val nextStepToRunIdx                                      = nextStepIdx.foldMap(_ + 1)

        <.div(
          ObserveStyles.SequenceControlForm,
          List(
            // Sync button
            syncButton(idName, p.canSync)
              .when(status.isIdle || status.isError),
            // Run button
            runButton(idName.id, partial, nextStepToRunIdx, p.canRun)
              .when(status.isIdle || status.isError),
            // Cancel pause button
            cancelPauseButton(idName.id, p.canCancelPause)
              .when(status.userStopRequested),
            // Pause button
            pauseButton(idName, p.canPause)
              .when(status.isRunning && !status.userStopRequested)
          ).toTagMod,
          subsystemsButton($, overrides).when(status.isIdle || status.isError)
        )
      }
      .build

}
