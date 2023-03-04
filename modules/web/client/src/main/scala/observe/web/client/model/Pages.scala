// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import cats._
import cats.syntax.all._
import diode.Action
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.router._
import monocle.Prism
import observe.model.Observation
import observe.model.StepId
import observe.model.enum._
import observe.web.client.actions._
import observe.web.client.circuit.ObserveCircuit

// Pages
object Pages {
  sealed trait ObservePages extends Product with Serializable

  // Indicates which step to display
  final case class StepIdDisplayed(step: Option[StepId])

  object StepIdDisplayed {
    implicit val equal: Eq[StepIdDisplayed] = Eq.fromUniversalEquals
  }

  case object Root                 extends ObservePages
  case object SoundTest            extends ObservePages
  case object CalibrationQueuePage extends ObservePages
  final case class PreviewPage(
    instrument: Instrument,
    obsId:      Observation.Id,
    stepId:     StepIdDisplayed
  ) extends ObservePages
  final case class PreviewConfigPage(instrument: Instrument, obsId: Observation.Id, stepId: StepId)
      extends ObservePages
  final case class SequencePage(
    instrument: Instrument,
    obsId:      Observation.Id,
    stepId:     StepIdDisplayed
  ) extends ObservePages
  final case class SequenceConfigPage(instrument: Instrument, obsId: Observation.Id, stepId: StepId)
      extends ObservePages

  implicit val equal: Eq[ObservePages] = Eq.instance {
    case (Root, Root)                                               =>
      true
    case (SoundTest, SoundTest)                                     =>
      true
    case (CalibrationQueuePage, CalibrationQueuePage)               =>
      true
    case (SequencePage(i, o, s), SequencePage(j, p, r))             =>
      i === j && o === p && s === r
    case (SequenceConfigPage(i, o, s), SequenceConfigPage(j, p, r)) =>
      i === j && o === p && s === r
    case (PreviewPage(i, o, s), PreviewPage(j, p, r))               =>
      i === j && o === p && s === r
    case (PreviewConfigPage(i, o, s), PreviewConfigPage(j, p, r))   =>
      i === j && o === p && s === r
    case _                                                          => false
  }

  // Pages forms a prism with Page
  val PageActionP: Prism[Action, ObservePages] = Prism[Action, ObservePages] {
    case SelectRoot                         => Root.some
    case RequestSoundEcho                   => SoundTest.some
    case SelectCalibrationQueue             => CalibrationQueuePage.some
    case SelectSequencePreview(i, id, step) => PreviewPage(i, id, step).some
    case ShowPreviewStepConfig(i, id, step) => PreviewConfigPage(i, id, step).some
    case SelectIdToDisplay(i, id, step)     => SequencePage(i, id, step).some
    case ShowStepConfig(i, id, step)        => SequenceConfigPage(i, id, step).some
  } {
    case Root                            => SelectRoot
    case SoundTest                       => RequestSoundEcho
    case CalibrationQueuePage            => SelectCalibrationQueue
    case PreviewPage(i, id, step)        => SelectSequencePreview(i, id, step)
    case PreviewConfigPage(i, id, step)  => ShowPreviewStepConfig(i, id, step)
    case SequencePage(i, id, step)       => SelectIdToDisplay(i, id, step)
    case SequenceConfigPage(i, id, step) => ShowStepConfig(i, id, step)
  }

  /**
   * Extensions methods for RouterCtl
   */
  implicit class RouterCtlOps(val r: RouterCtl[ObservePages]) extends AnyVal {

    /**
     * Some pages are linked to actions. This methods lets you set the url and dispatch an action at
     * the same time
     */
    def setUrlAndDispatchCB(b: ObservePages): Callback =
      r.set(b) *> ObserveCircuit.dispatchCB(PageActionP.reverseGet(b))

    /**
     * Some actions are linked to a page. This methods lets you dispatch and action and set the url
     */
    def dispatchAndSetUrlCB(b: Action): Callback =
      PageActionP.getOption(b).map(r.set).getOrEmpty *>
        ObserveCircuit.dispatchCB(b)

  }
}
