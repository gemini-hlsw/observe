// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import react.common.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import observe.model.ExecutionStep
import observe.model.ClientStatus
import lucuma.core.model.Observation
import lucuma.core.enums.Instrument
import observe.model.enums.SequenceState
import lucuma.core.model.sequence.Step
import observe.ui.model.TabOperations
import cats.syntax.all.*
import observe.model.enums.StepState
import alleycats.Empty

case class StepProgressCell(
  clientStatus:  ClientStatus,
  step:          ExecutionStep,
  stepIndex:     Int,
  obsId:         Observation.Id,
  instrument:    Instrument,
  tabOperations: TabOperations,
  sequenceState: SequenceState,
  selectedStep:  Option[Step.Id],
  isPreview:     Boolean
) extends ReactFnProps(StepProgressCell.component):
  def stepSelected(i: Step.Id): Boolean =
    selectedStep.exists(_ === i) && !isPreview &&
      (clientStatus.isLogged || tabOperations.resourceRunNotIdle(i))

object StepProgressCell:
  private type Props = StepProgressCell

  private val component = ScalaFnComponent[Props](props =>
    (props.sequenceState, props.step) match
      case (f, s) if s.status === StepState.Running && s.fileId.isEmpty && f.userStopRequested =>
        // Case pause at the sequence level
        // stepObservationPausing(props)
        EmptyVdom
      case (_, s) if s.status === StepState.Running && s.fileId.isEmpty                        =>
        // Case configuring, label and status icons
        // stepSystemsStatus(s)
        EmptyVdom
      case (_, s) if s.isObservePaused && s.fileId.isDefined                                   =>
        // Case for exposure paused, label and control buttons
        // stepObservationStatusAndFile(props, s.fileId.orEmpty, paused = true)
        EmptyVdom
      case (_, s) if s.status === StepState.Running && s.fileId.isDefined                      =>
        // Case for a exposure onging, progress bar and control buttons
        // stepObservationStatusAndFile(props, s.fileId.orEmpty, paused = false)
        EmptyVdom
      case (_, s) if s.wasSkipped                                                              =>
        <.p("Skipped")
      case (_, _) if props.step.skip                                                           =>
        <.p("Skip")
      case (_, s) if s.status === StepState.Completed && s.fileId.isDefined                    =>
        // <.p(ObserveStyles.componentLabel, s.fileId.orEmpty)
        EmptyVdom
      case (_, s) if props.stepSelected(s.id) && s.canConfigure                                =>
        // stepSubsystemControl(props)
        EmptyVdom
      case _                                                                                   =>
        // <.p(ObserveStyles.componentLabel, props.step.show)
        EmptyVdom
  )
