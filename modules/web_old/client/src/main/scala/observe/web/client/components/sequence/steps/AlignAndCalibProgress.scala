// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence.steps

import scala.math.max

import cats.Show
import cats.syntax.all.*
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.util.Enumerated
import lucuma.react.common.*
import lucuma.react.semanticui.colors.*
import lucuma.react.semanticui.modules.progress.Progress
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.components.ObserveStyles
import observe.web.client.model.AlignAndCalibStep
import observe.web.client.model.AlignAndCalibStep.*
import observe.web.client.model.StepItems.StepStateSummary
import observe.web.client.reusability.*

final case class ACProgressBar(
  step:  AlignAndCalibStep,
  state: StepStateSummary
) extends ReactProps[ACProgressBar](ACProgressBar.component)

object ACProgressBar {
  type Props = ACProgressBar

  final case class State(counter: Int, msg: String)

  object State {
    def initialStateFromProps(p: Props): State =
      p.step match {
        case Done => State(0, "Preparing...")
        case x    => State(stepsOrdering.indexOf(x), x.show)
      }
  }

  given Reusability[Props] = Reusability.derive[Props]
  given Reusability[State] = Reusability.derive[State]

  val acSteps                   = Enumerated[AlignAndCalibStep]
  given Show[AlignAndCalibStep] = Show.show {
    case NoAction           => ""
    case StartGuiding       => "Start Guiding"
    case StopGuiding        => "Stop Guiding"
    case SuperContOff       => "SuperContinuum Off"
    case OMSSEntShutterOff  => "Entrance shutter closed"
    case CalExistShutterOff => "CAL Exit shutter closed"
    case ArtSourceDeploy    => "Artificial Source deploy"
    case AoDarks            => "Take AO Darks"
    case SuperContOn        => "SuperContinumm On"
    case CalFlags           => "Set CAL flags"
    case Twt2Lens           => "TWT to Lens"
    case CalExitShutterOn   => "CAL Exit shutten opened"
    case ArtSourceExtract   => "Artificial Source extract"
    case OMSSEntShutterOn   => "Entrance shutter opened"
    case InputFoldTracking  => "Input fold tracking"
    case Done               => "Done"
  }

  // The typical AC sequence is like this
  // We use it to guestimate the location when the component is created
  val stepsOrdering = List(
    SuperContOff,
    OMSSEntShutterOff,
    CalExistShutterOff,
    ArtSourceDeploy,
    AoDarks,
    SuperContOn,
    CalFlags,
    Twt2Lens,
    StartGuiding,
    StopGuiding,
    SuperContOff,
    ArtSourceExtract,
    OMSSEntShutterOn,
    InputFoldTracking,
    Done
  )

  protected val component = ScalaComponent
    .builder[Props]("ACProgressBar")
    .initialStateFromProps(State.initialStateFromProps)
    .render_PS { (p, s) =>
      val isInError = !p.state.isACRunning && p.state.isACInError
      val msg       = if (isInError) "Error" else s.msg
      Progress(
        total = acSteps.all.length - 1,
        value = max(0, s.counter),
        color = if (isInError) Red else Green,
        clazz = ObserveStyles.observationProgressBar
      )(s"Align and Calib: $msg")
    }
    .getDerivedStateFromProps((p, s) =>
      (State.counter.modify(_ + 1) >>> Focus[State](_.msg).replace(p.step.show))(s)
    )
    .configure(Reusability.shouldComponentUpdate)
    .build
}

/**
 * Component to wrap the progress bar
 */
final case class AlignAndCalibProgress(state: StepStateSummary)
    extends ReactProps[AlignAndCalibProgress](AlignAndCalibProgress.component) {

  protected[steps] val connect =
    ObserveCircuit.connect(ObserveCircuit.acProgressRW)
}

object AlignAndCalibProgress {
  type Props = AlignAndCalibProgress

  given Reusability[Props] = Reusability.derive[Props]

  protected val component = ScalaComponent
    .builder[Props]("AlignAndCalibProgress")
    .stateless
    .render_P(p => p.connect(s => ACProgressBar(s(), p.state)))
    .configure(Reusability.shouldComponentUpdate)
    .build
}
