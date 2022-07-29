// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.queue

import cats.syntax.all._
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import react.semanticui.colors._
import observe.model.QueueId
import observe.web.client.actions.RequestAllSelectedSequences
import observe.web.client.actions.RequestClearAllCal
import observe.web.client.actions.RequestRunCal
import observe.web.client.actions.RequestStopCal
import observe.web.client.circuit._
import observe.web.client.components.ObserveStyles
import observe.web.client.icons._
import observe.web.client.model.AddDayCalOperation
import observe.web.client.model.ClearAllCalOperation
import observe.web.client.model.RunCalOperation
import observe.web.client.model.StopCalOperation
import observe.web.client.reusability._
import observe.web.client.semanticui.controlButton

final case class CalQueueToolbar(queueId: QueueId, control: CalQueueControlFocus)
    extends ReactProps[CalQueueToolbar](CalQueueToolbar.component) {

  val canOperate: Boolean = control.canOperate

  val clearCalRunning: Boolean =
    control.ops.clearAllCalRequested === ClearAllCalOperation.ClearAllCalInFlight

  val addDayCalRunning: Boolean =
    control.ops.addDayCalRequested === AddDayCalOperation.AddDayCalInFlight

  val runRunning: Boolean =
    control.ops.runCalRequested === RunCalOperation.RunCalInFlight

  val stopRunning: Boolean =
    control.ops.stopCalRequested === StopCalOperation.StopCalInFlight

  val anyInFlight: Boolean =
    clearCalRunning || addDayCalRunning || runRunning || stopRunning

  val queueRunning: Boolean = control.execState.running
}

/**
 * Toolbar for logged in users
 */
object CalQueueToolbar {

  type Props = CalQueueToolbar

  implicit val propsReuse: Reusability[Props] = Reusability.derive[Props]

  def allDayCal(id: QueueId): Callback =
    ObserveCircuit.dispatchCB(RequestAllSelectedSequences(id))

  def clearAllCal(id: QueueId): Callback =
    ObserveCircuit.dispatchCB(RequestClearAllCal(id))

  def runCal(id: QueueId): Callback =
    ObserveCircuit.dispatchCB(RequestRunCal(id))

  def stopCal(id: QueueId): Callback =
    ObserveCircuit.dispatchCB(RequestStopCal(id))

  private def addAllButton(p: Props) =
    controlButton(
      icon =
        if (p.addDayCalRunning) IconRefresh.loading(true)
        else IconCloneOutline,
      color = Blue,
      onClick = allDayCal(p.queueId),
      disabled = !p.canOperate || p.anyInFlight || p.queueRunning,
      tooltip =
        if (p.control.selectedSeq > 0)
          s"Add ${p.control.selectedSeq} sequences on the session queue"
        else "Add all sequences on the session queue",
      text =
        if (p.control.selectedSeq > 0) s"Add ${p.control.selectedSeq} seqs."
        else "Add all"
    )

  private def clearAllButton(p: Props) =
    controlButton(
      icon =
        if (p.clearCalRunning) IconRefresh.loading(true)
        else IconTrashOutline,
      color = Brown,
      onClick = clearAllCal(p.queueId),
      disabled = !p.canOperate || p.anyInFlight || p.queueRunning,
      tooltip = "Remove all sequences on the calibration queue",
      text = "Clear"
    )

  private def runButton(p: Props) =
    controlButton(
      icon =
        if (p.runRunning) IconRefresh.loading(true)
        else IconPlay,
      color = Blue,
      onClick = runCal(p.queueId),
      disabled = !p.canOperate || p.anyInFlight,
      tooltip = "Run the calibration queue",
      text = "Run"
    )

  private def stopButton(p: Props) =
    controlButton(
      icon =
        if (p.runRunning) IconRefresh.loading(true)
        else IconStop,
      color = Teal,
      onClick = stopCal(p.queueId),
      disabled = !p.canOperate || p.anyInFlight,
      tooltip = "Stop the calibration queue",
      text = "Stop"
    )

  private val component = ScalaComponent
    .builder[Props]
    .render_P(p =>
      <.div(
        ObserveStyles.SequencesControl,
        addAllButton(p).unless(p.queueRunning),
        clearAllButton(p).unless(p.queueRunning || p.control.queueSize === 0),
        runButton(p).unless(p.queueRunning || p.control.queueSize === 0),
        stopButton(p).when(p.queueRunning)
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

}
