// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.*
import lucuma.react.SizePx
import lucuma.react.common.*
import lucuma.react.syntax.*
import lucuma.react.table.*
import lucuma.schemas.model.enums.StepExecutionState
import lucuma.ui.sequence.*
import lucuma.ui.table.*
import lucuma.ui.table.ColumnSize.*
import lucuma.ui.table.hooks.*
import observe.model.ExecutionState
import observe.model.StepProgress
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.sequence.steps.*
import observe.ui.model.EditableQaFields
import observe.ui.model.ObservationRequests
import observe.ui.model.enums.ClientMode

import scalajs.js

// Offload SequenceTable definitions to improve legibility.
trait SequenceTableDefs[D] extends SequenceRowBuilder[D]:
  protected def instrument: Instrument

  protected case class TableMeta(
    requests:           ObservationRequests,
    executionState:     ExecutionState,
    progress:           Option[StepProgress],
    selectedStepId:     Option[Step.Id],
    datasetIdsInFlight: Set[Dataset.Id],
    onBreakpointFlip:   (Observation.Id, Step.Id) => Callback,
    onDatasetQaChange:  Dataset.Id => EditableQaFields => Callback
  )

  protected val ColDef = ColumnDef[SequenceTableRowType].WithTableMeta[TableMeta]

  // Breakpoint column has width 0 but is translated and actually shown.
  // We display an empty BreakpointSpace column to show the space with correct borders.
  protected val HeaderColumnId: ColumnId          = ColumnId("header")
  protected val BreakpointColumnId: ColumnId      = ColumnId("breakpoint")
  protected val BreakpointSpaceColumnId: ColumnId = ColumnId("breakpointDummy")
  // Will be rendered as a full-width column in an extra row
  protected val ExtraRowColumnId: ColumnId        = ColumnId("extraRow")
  // protected val ObsModeColumnId: ColumnId         = ColumnId("obsMode")
  protected val CameraColumnId: ColumnId          = ColumnId("camera")
  protected val DeckerColumnId: ColumnId          = ColumnId("decker")
  protected val ReadModeColumnId: ColumnId        = ColumnId("readMode")
  protected val ImagingMirrorColumnId: ColumnId   = ColumnId("imagingMirror")
  protected val SettingsColumnId: ColumnId        = ColumnId("settings")

  protected lazy val ColumnSizes: Map[ColumnId, ColumnSize] = Map(
    HeaderColumnId          -> FixedSize(0.toPx),
    BreakpointColumnId      -> FixedSize(0.toPx),
    BreakpointSpaceColumnId -> FixedSize(30.toPx),
    ExtraRowColumnId        -> FixedSize(0.toPx),
    CameraColumnId          -> Resizable(10.toPx),
    DeckerColumnId          -> Resizable(10.toPx),
    ReadModeColumnId        -> Resizable(180.toPx),
    ImagingMirrorColumnId   -> Resizable(10.toPx),
    SettingsColumnId        -> FixedSize(39.toPx)
  ) ++ SequenceColumns.BaseColumnSizes(instrument)

  // The order in which they are removed by overflow. The ones at the beginning go first.
  // Missing columns are not removed by overflow. (We declare them in reverse order)
  protected lazy val ColumnPriorities: List[ColumnId] =
    List(
      CameraColumnId,
      DeckerColumnId,
      ReadModeColumnId,
      ImagingMirrorColumnId,
      SettingsColumnId
    ).reverse ++ SequenceColumns.BaseColumnPriorities(instrument)

  protected lazy val DynTableDef = DynTable(
    ColumnSizes,
    ColumnPriorities,
    DynTable.ColState(
      resized = ColumnSizing(),
      visibility = ColumnVisibility(
        CameraColumnId        -> Visibility.Hidden,
        DeckerColumnId        -> Visibility.Hidden,
        ReadModeColumnId      -> Visibility.Hidden,
        ImagingMirrorColumnId -> Visibility.Hidden
      )
    )
  )

// [T, A, TM, CM, TF, CF, FM]
  private def column[V](
    id:     ColumnId,
    header: VdomNode,
    cell:   js.UndefOr[CellContext[SequenceTableRowType, V, TableMeta, ?, ?, ?, ?] => VdomNode] =
      js.undefined
  ): ColDef.TypeFor[V] =
    ColDef(id, header = _ => header, cell = cell)

  protected def columnDefs(
    clientMode: ClientMode,
    instrument: Instrument,
    obsId:      Observation.Id,
    isPreview:  Boolean
  ): List[ColDef.TypeFor[?]] =
    List(
      SequenceColumns
        .headerCell(HeaderColumnId, ColDef)
        .withColumnSize(ColumnSizes(HeaderColumnId)),
      column(
        BreakpointColumnId,
        "",
        cell =>
          (cell.row.original.value.toOption, cell.table.options.meta).mapN: (stepRow, meta) =>
            val step: SequenceRow[D]    = stepRow.step
            val stepId: Option[Step.Id] = step.id.toOption
            // val canSetBreakpoint =
            //   clientStatus.canOperate && step.get.canSetBreakpoint(
            //     execution.map(_.steps).orEmpty
            //   )

            <.div(
              <.div(
                ObserveStyles.BreakpointHandle,
                stepId
                  .map: sId =>
                    ^.onClick ==> (_.stopPropagationCB >> meta.onBreakpointFlip(obsId, sId))
                  .whenDefined
              )(
                Icons.CircleSolid
                  .withFixedWidth()
                  .withClass(
                    ObserveStyles.BreakpointIcon |+|
                      // ObserveStyles.FlippableBreakpoint.when_(canSetBreakpoint) |+|
                      ObserveStyles.ActiveBreakpoint
                        .when_(stepId.exists(meta.executionState.breakpoints.contains_(_)))
                  )
              ).when(
                cell.row.index.toInt > 0 &&
                  List(StepTime.Present, StepTime.Future).contains_(step.stepTime) &&
                  meta.executionState.runningStepId =!= stepId
              )
            )
      ),
      column(BreakpointSpaceColumnId, "", _ => EmptyVdom),
      column(
        ExtraRowColumnId,
        "",
        cell =>
          cell.table.options.meta.map: meta =>
            cell.row.original.value.toOption
              .map(_.step)
              .map[VdomNode]:
                case step @ SequenceRow.Executed.ExecutedStep(_, _) =>
                  renderVisitExtraRow(
                    step,
                    showOngoingLabel = false,
                    step.executionState match
                      case StepExecutionState.Completed | StepExecutionState.Stopped =>
                        QaEditor(_, _, meta.onDatasetQaChange)
                      case _                                                         =>
                        (_, _) => EmptyVdom // Don't display QA editor for non-completed steps
                    ,
                    meta.datasetIdsInFlight
                  )
                case step                                           =>
                  (step.id.toOption, step.stepTypeDisplay, step.exposureTime).mapN:
                    (stepId, stepType, exposureTime) =>
                      meta.selectedStepId
                        .filter(_ === stepId)
                        .map: stepId =>
                          StepProgressCell(
                            clientMode = clientMode,
                            instrument = instrument,
                            stepId = stepId,
                            stepType = stepType,
                            isFinished = step.isFinished,
                            obsId = obsId,
                            requests = meta.requests,
                            runningStepId = meta.executionState.runningStepId,
                            sequenceState = meta.executionState.sequenceState,
                            isPausedInStep =
                              meta.executionState.pausedStep.exists(_.value === stepId),
                            subsystemStatus = meta.executionState.stepResources
                              .find(_._1 === stepId)
                              .map(_._2.toMap)
                              .getOrElse(Map.empty),
                            systemOverrides = meta.executionState.systemOverrides,
                            exposureTime = exposureTime,
                            progress = meta.progress,
                            selectedStep = meta.selectedStepId,
                            isPreview = isPreview
                          )
      )
    ) ++
      SequenceColumns(ColDef, _._1.some, _._2.some)(instrument)
        .map(colDef => colDef.withColumnSize(ColumnSizes(colDef.id))) ++
      List(
        // column(ObsModeColumnId, "Observing Mode"),
        column(CameraColumnId, "Camera"),
        column(DeckerColumnId, "Decker"),
        column(ReadModeColumnId, "ReadMode"),
        column(ImagingMirrorColumnId, "ImagingMirror"),
        column(
          SettingsColumnId,
          Icons.RectangleList,
          _ => SettingsCell() // TODO
        )
      )
