// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.toolbars

import cats.syntax.all._
import diode.react.ReactConnectProxy
import japgolly.scalajs.react.React
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import mouse.boolean._
import react.common._
import react.semanticui.elements.button.Button
import react.semanticui.elements.button.ButtonGroup
import react.semanticui.elements.button.LabelPosition
import react.semanticui.elements.label.Label
import react.semanticui.sizes._
import observe.model.{ Observation, RunningStep, StepId }
import observe.model.enum.Instrument
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.circuit.SequenceInfoFocus
import observe.web.client.components.ObserveStyles
import observe.web.client.icons._
import observe.web.client.model.Pages._

final case class StepConfigToolbar(
  router:     RouterCtl[ObservePages],
  instrument: Instrument,
  id:         Observation.Id,
  stepId:     StepId,
  steps:      List[StepId],
  isPreview:  Boolean
) extends ReactProps[StepConfigToolbar](StepConfigToolbar.component) {
  val sequenceConnect: ReactConnectProxy[Option[SequenceInfoFocus]] =
    ObserveCircuit.connect(ObserveCircuit.sequenceObserverReader(id))
}

/**
 * Toolbar when displaying a step configuration
 */
object StepConfigToolbar {
  type Props = StepConfigToolbar

  private def prevStepId(id: StepId, l: List[StepId]): Option[StepId] =
    l.takeWhile(_ =!= id).lastOption
  private def nextStepId(id: StepId, l: List[StepId]): Option[StepId] =
    l.dropWhile(_ =!= id).drop(1).headOption

  private val component                                               = ScalaComponent
    .builder[Props]("StepConfigToolbar")
    .stateless
    .render_P { p =>
      val sequencePage = if (p.isPreview) {
        PreviewPage(p.instrument, p.id, StepIdDisplayed(p.stepId.some))
      } else {
        SequencePage(p.instrument, p.id, StepIdDisplayed(p.stepId.some))
      }
      val nextStepPage = nextStepId(p.stepId, p.steps).map { x =>
        if (p.isPreview) {
          PreviewConfigPage(p.instrument, p.id, x)
        } else {
          SequenceConfigPage(p.instrument, p.id, x)
        }
      }
      val prevStepPage = prevStepId(p.stepId, p.steps).map { x =>
        if (p.isPreview) {
          PreviewConfigPage(p.instrument, p.id, x)
        } else {
          SequenceConfigPage(p.instrument, p.id, x)
        }
      }

      <.div(
        ObserveStyles.ConfigTableControls,
        <.div(ObserveStyles.SequenceControlButtons)(
          // Back to sequence button
          p.router.link(sequencePage)(
            Button(icon = true,
                   labelPosition = LabelPosition.Left,
                   onClick = p.router.setUrlAndDispatchCB(sequencePage)
            )(IconChevronLeft, "Back")
          )
        ),
        p.sequenceConnect(_() match {
          case Some(p) => SequenceInfo(p)
          case _       => React.Fragment()
        }),
        <.div(ObserveStyles.SequenceInfo)(
          ButtonGroup(clazz = Css("right floated"))(
            // Previous step button
            prevStepPage.fold[VdomNode](
              Button(icon = true, labelPosition = LabelPosition.Left, disabled = true)(
                IconChevronLeft,
                "Prev"
              )
            )(x =>
              p.router.link(x)(
                Button(icon = true,
                       labelPosition = LabelPosition.Left,
                       onClick = p.router.setUrlAndDispatchCB(x)
                )(IconChevronLeft, "Prev")
              )
            ),
            Label(size = Large, clazz = ObserveStyles.labelAsButton)(
              p.steps
                .indexOf(p.stepId)
                .some
                .flatMap(x => (x >= 0).option(x))
                .flatMap(RunningStep.fromInt(p.stepId.some, _, p.steps.length))
                .getOrElse(RunningStep.Zero)
                .show
            ),
            // Next step button
            nextStepPage.fold[VdomNode](
              Button(icon = true, labelPosition = LabelPosition.Right, disabled = true)(
                IconChevronRight,
                "Next"
              )
            )(x =>
              p.router.link(x)(
                Button(icon = true,
                       labelPosition = LabelPosition.Right,
                       onClick = p.router.setUrlAndDispatchCB(x)
                )(IconChevronRight, "Next")
              )
            )
          )
        )
      )
    }
    .build

  def apply(p: Props): Unmounted[Props, Unit, Unit] = component(p)
}
