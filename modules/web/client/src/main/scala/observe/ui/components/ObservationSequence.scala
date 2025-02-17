// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.effect.IO
import cats.syntax.all.*
import crystal.react.*
import japgolly.scalajs.react.*
import lucuma.core.enums.Breakpoint
import lucuma.core.model.Observation
import lucuma.core.model.sequence.InstrumentExecutionConfig
import lucuma.core.model.sequence.Step
import lucuma.react.common.ReactFnComponent
import lucuma.react.common.ReactFnProps
import lucuma.react.primereact.Message
import lucuma.schemas.model.AtomRecord
import lucuma.schemas.model.Dataset
import lucuma.schemas.model.ExecutionVisits
import lucuma.schemas.model.StepRecord
import lucuma.schemas.model.Visit
import lucuma.ui.syntax.toast.*
import monocle.Optional
import monocle.Traversal
import observe.model.ExecutionState
import observe.model.StepProgress
import observe.model.odb.RecordedVisit
import observe.ui.components.sequence.byInstrument.*
import observe.ui.model.AppContext
import observe.ui.model.EditableQaFields
import observe.ui.model.ObservationRequests
import observe.ui.model.enums.ClientMode
import observe.ui.services.ODBQueryApi
import observe.ui.services.SequenceApi

import scala.collection.immutable.HashSet

case class ObservationSequence(
  obsId:                Observation.Id,
  config:               InstrumentExecutionConfig,
  visits:               View[Option[ExecutionVisits]],
  executionState:       View[ExecutionState],
  currentRecordedVisit: Option[RecordedVisit],
  progress:             Option[StepProgress],
  requests:             ObservationRequests,
  selectedStep:         Option[Step.Id],
  setSelectedStep:      Step.Id => Callback,
  clientMode:           ClientMode
) extends ReactFnProps(ObservationSequence)

object ObservationSequence
    extends ReactFnComponent[ObservationSequence](props =>
      val gmosNorthDatasets: Traversal[ExecutionVisits, List[Dataset]] =
        ExecutionVisits.gmosNorth
          .andThen(ExecutionVisits.GmosNorth.visits)
          .each
          .andThen(Visit.GmosNorth.atoms)
          .each
          .andThen(AtomRecord.GmosNorth.steps)
          .each
          .andThen(StepRecord.GmosNorth.datasets)

      val gmosSouthDatasets: Traversal[ExecutionVisits, List[Dataset]] =
        ExecutionVisits.gmosSouth
          .andThen(ExecutionVisits.GmosSouth.visits)
          .each
          .andThen(Visit.GmosSouth.atoms)
          .each
          .andThen(AtomRecord.GmosSouth.steps)
          .each
          .andThen(StepRecord.GmosSouth.datasets)

      // This is only lawful if the traverse returns 0 or 1 instances of A.
      def unsafeHeadOption[T, A](traversal: Traversal[T, A]): Optional[T, A] =
        Optional[T, A](traversal.getAll(_).headOption)(traversal.replace)

      def instrumentDatasetWithId(traversal: Traversal[ExecutionVisits, List[Dataset]])(
        datasetId: Dataset.Id
      ): Optional[ExecutionVisits, Dataset] =
        unsafeHeadOption(traversal.each.filter(dataset => dataset.id === datasetId))

      def datasetWithId(datasetId: Dataset.Id): Traversal[ExecutionVisits, Dataset] =
        Traversal.applyN(
          instrumentDatasetWithId(gmosNorthDatasets)(datasetId),
          instrumentDatasetWithId(gmosSouthDatasets)(datasetId)
        )

      for
        ctx                <- useContext(AppContext.ctx)
        sequenceApi        <- useContext(SequenceApi.ctx)
        odbQueryApi        <- useContext(ODBQueryApi.ctx)
        datasetIdsInFlight <- useState(HashSet.empty[Dataset.Id])
      yield
        import ctx.given

        val breakpoints: View[Set[Step.Id]] =
          props.executionState.zoom(ExecutionState.breakpoints)

        val onBreakpointFlip: (Observation.Id, Step.Id, Breakpoint) => Callback =
          (obsId, stepId, value) =>
            breakpoints
              .mod(set => if (set.contains(stepId)) set - stepId else set + stepId) >>
              sequenceApi.setBreakpoint(obsId, stepId, value).runAsync

        val onDatasetQAChange: Dataset.Id => EditableQaFields => Callback =
          datasetId =>
            qaFields =>
              props.visits.toOptionView.map { visits =>
                def datasetQaView(datasetId: Dataset.Id): ViewList[EditableQaFields] =
                  visits.zoom:
                    datasetWithId(datasetId).andThen(EditableQaFields.fromDataset)

                datasetIdsInFlight.modState(_ + datasetId) >>
                  odbQueryApi
                    .updateDatasetQa(datasetId, qaFields)
                    .flatMap: _ =>
                      (datasetQaView(datasetId).set(qaFields) >>
                        datasetIdsInFlight.modState(_ - datasetId))
                        .to[IO]
                    .handleErrorWith: e =>
                      (datasetIdsInFlight.modState(_ - datasetId) >>
                        ctx.toast.show(
                          s"Error updating dataset QA state for $datasetId: ${e.getMessage}",
                          Message.Severity.Error,
                          sticky = true
                        )).to[IO]
                    .runAsync
              }.orEmpty // If there are no visits, there's nothing to change.

        props.config match // TODO Show visits even if sequence data is not available
          case InstrumentExecutionConfig.GmosNorth(config) =>
            GmosNorthSequenceTable(
              props.clientMode,
              props.obsId,
              config,
              props.visits.get
                .collect:
                  case ExecutionVisits.GmosNorth(visits) => visits.toList
                .orEmpty,
              props.executionState.get,
              props.currentRecordedVisit,
              props.progress,
              props.selectedStep,
              props.setSelectedStep,
              props.requests,
              isPreview = false,
              onBreakpointFlip,
              onDatasetQAChange,
              datasetIdsInFlight.value
            )
          case InstrumentExecutionConfig.GmosSouth(config) =>
            GmosSouthSequenceTable(
              props.clientMode,
              props.obsId,
              config,
              props.visits.get
                .collect:
                  case ExecutionVisits.GmosSouth(visits) => visits.toList
                .orEmpty,
              props.executionState.get,
              props.currentRecordedVisit,
              props.progress,
              props.selectedStep,
              props.setSelectedStep,
              props.requests,
              isPreview = false,
              onBreakpointFlip,
              onDatasetQAChange,
              datasetIdsInFlight.value
            )
    )
