// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import eu.timepit.refined.types.numeric.NonNegInt
import japgolly.scalajs.react.*
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.model.Observation
import lucuma.core.model.sequence.*
import lucuma.core.model.sequence.gmos.*
import lucuma.react.resizeDetector.hooks.*
import lucuma.schemas.model.Visit
import lucuma.ui.sequence.*
import observe.model.ExecutionState
import observe.model.ObserveStep
import observe.model.StepProgress
import observe.model.odb.RecordedVisit
import observe.ui.components.sequence.steps.*
import observe.ui.model.EditableQaFields
import observe.ui.model.ObservationRequests
import observe.ui.model.enums.ClientMode

// Helper to build Props classes for instrument sequence tables.
private trait SequenceTable[S, D <: DynamicConfig](
  protected[sequence] val instrument:    Instrument,
  protected[sequence] val nodAndShuffle: Option[GmosNodAndShuffle]
):
  def clientMode: ClientMode
  def obsId: Observation.Id
  def config: ExecutionConfig[S, D]
  def visits: List[Visit[D]]
  def executionState: ExecutionState
  def currentRecordedVisit: Option[RecordedVisit]
  def progress: Option[StepProgress]
  def selectedStepId: Option[Step.Id]
  def setSelectedStepId: Step.Id => Callback
  def requests: ObservationRequests
  def isPreview: Boolean
  def onBreakpointFlip: (Observation.Id, Step.Id, Breakpoint) => Callback
  def onDatasetQaChange: Dataset.Id => EditableQaFields => Callback
  def datasetIdsInFlight: Set[Dataset.Id]

  protected[sequence] lazy val currentRecordedStepId: Option[Step.Id] =
    currentRecordedVisit.flatMap(RecordedVisit.stepId.getOption).map(_.value)

  private def futureSteps(atoms: List[Atom[D]]): List[SequenceRow.FutureStep[D]] =
    SequenceRow.FutureStep.fromAtoms(atoms, _ => none) // TODO Pass signal to noise

  protected[sequence] lazy val currentAtomPendingSteps: List[ObserveStep] =
    executionState.loadedSteps.filterNot(_.isFinished)

  protected[sequence] def currentStepsToRows(
    currentSteps: List[ObserveStep]
  ): List[CurrentAtomStepRow[D]] =
    currentSteps.map: step =>
      CurrentAtomStepRow(
        step,
        breakpoint =
          if (executionState.breakpoints.contains_(step.id)) Breakpoint.Enabled
          else Breakpoint.Disabled,
        isFirstOfAtom = currentSteps.headOption.exists(_.id === step.id)
      )

  protected[sequence] lazy val (currentAcquisitionRows, currentScienceRows)
    : (List[SequenceRow[D]], List[SequenceRow[D]]) =
    executionState.sequenceType match
      case SequenceType.Acquisition =>
        (currentStepsToRows(currentAtomPendingSteps),
         config.science.map(s => futureSteps(List(s.nextAtom))).orEmpty
        )
      case SequenceType.Science     =>
        (config.acquisition.map(a => futureSteps(List(a.nextAtom))).orEmpty,
         currentStepsToRows(currentAtomPendingSteps)
        )

  protected[sequence] lazy val scienceRows: List[SequenceRow[D]] =
    currentScienceRows ++ config.science.map(s => futureSteps(s.possibleFuture)).orEmpty

  protected[sequence] lazy val acquisitionRows: List[SequenceRow[D]] =
    // If initial acquisition atom is complete, then nextAtom already shows the next potential step. We want to hide that.
    // We also hide acquisition if the sequence is complete
    if executionState.isWaitingAcquisitionPrompt || executionState.sequenceType === SequenceType.Science || scienceRows.isEmpty
    then List.empty
    else currentAcquisitionRows

  // Alert position is right after currently executing atom.
  protected[sequence] lazy val alertPosition: NonNegInt =
    NonNegInt.unsafeFrom(currentAtomPendingSteps.length)

  protected[sequence] lazy val runningStepId: Option[Step.Id] = executionState.runningStepId

  protected[sequence] lazy val nextStepId: Option[Step.Id] =
    currentAtomPendingSteps.headOption.map(_.id)
