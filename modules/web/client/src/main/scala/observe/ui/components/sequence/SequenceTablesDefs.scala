// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.*
import lucuma.core.model.sequence.gmos.*
import lucuma.react.SizePx
import lucuma.react.common.*
import lucuma.react.syntax.*
import lucuma.react.table.*
import lucuma.typed.{tanstackTableCore => raw}
import lucuma.ui.sequence.SequenceColumns.*
import lucuma.ui.sequence.*
import lucuma.ui.table.ColumnSize.*
import lucuma.ui.table.*
import lucuma.ui.table.hooks.*
import observe.model.ExecutionState
import observe.model.StepProgress
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.sequence.steps.*
import observe.ui.model.ObservationRequests
import observe.ui.model.enums.ClientMode

import scalajs.js

// Offload SequenceTables definitions to improve legibility.
trait SequenceTablesDefs:
  protected type SequenceTableRowType = Expandable[HeaderOrRow[SequenceTableRow]]
  protected def ColDef = ColumnDef[SequenceTableRowType]

  // Breakpoint column has width 0 but is translated and actually shown.
  // We display an empty BreakpointSpace column to show the space with correct borders.
  protected val HeaderColumnId: ColumnId          = ColumnId("header")
  protected val BreakpointColumnId: ColumnId      = ColumnId("breakpoint")
  protected val BreakpointSpaceColumnId: ColumnId = ColumnId("breakpointDummy")
  protected val RunningStateColumnId: ColumnId    = ColumnId("runningState")
  // protected val ObsModeColumnId: ColumnId         = ColumnId("obsMode")
  protected val CameraColumnId: ColumnId          = ColumnId("camera")
  protected val DeckerColumnId: ColumnId          = ColumnId("decker")
  protected val ReadModeColumnId: ColumnId        = ColumnId("readMode")
  protected val ImagingMirrorColumnId: ColumnId   = ColumnId("imagingMirror")
  protected val DatasetsColumnId: ColumnId        = ColumnId("datasets")
  protected val SettingsColumnId: ColumnId        = ColumnId("settings")

  protected val ColumnSizes: Map[ColumnId, ColumnSize] = Map(
    HeaderColumnId          -> FixedSize(0.toPx),
    BreakpointColumnId      -> FixedSize(0.toPx),
    BreakpointSpaceColumnId -> FixedSize(30.toPx),
    IndexAndTypeColumnId    -> FixedSize(60.toPx),
    RunningStateColumnId    -> FixedSize(0.toPx),
    ExposureColumnId        -> Resizable(77.toPx, min = 77.toPx.some, max = 130.toPx.some),
    GuideColumnId           -> FixedSize(33.toPx),
    PColumnId               -> FixedSize(75.toPx),
    QColumnId               -> FixedSize(75.toPx),
    WavelengthColumnId      -> Resizable(75.toPx, min = 75.toPx.some, max = 130.toPx.some),
    FPUColumnId             -> Resizable(132.toPx, min = 132.toPx.some, max = 180.toPx.some),
    GratingColumnId         -> Resizable(120.toPx, min = 120.toPx.some, max = 180.toPx.some),
    FilterColumnId          -> Resizable(90.toPx, min = 90.toPx.some, max = 150.toPx.some),
    XBinColumnId            -> FixedSize(60.toPx),
    YBinColumnId            -> FixedSize(60.toPx),
    ROIColumnId             -> Resizable(75.toPx, min = 75.toPx.some, max = 130.toPx.some),
    SNColumnId              -> Resizable(75.toPx, min = 75.toPx.some, max = 130.toPx.some),
    CameraColumnId          -> Resizable(10.toPx),
    DeckerColumnId          -> Resizable(10.toPx),
    ReadModeColumnId        -> Resizable(180.toPx),
    ImagingMirrorColumnId   -> Resizable(10.toPx),
    DatasetsColumnId        -> Resizable(300.toPx, min = 75.toPx.some),
    SettingsColumnId        -> FixedSize(39.toPx)
  )

  // The order in which they are removed by overflow. The ones at the beginning go first.
  // Missing columns are not removed by overflow.
  protected val ColumnPriorities: List[ColumnId] = List(
    PColumnId,
    QColumnId,
    GuideColumnId,
    ExposureColumnId,
    SNColumnId,
    ROIColumnId,
    XBinColumnId,
    YBinColumnId,
    FilterColumnId,
    GratingColumnId,
    FPUColumnId,
    CameraColumnId,
    DeckerColumnId,
    ReadModeColumnId,
    ImagingMirrorColumnId,
    SettingsColumnId,
    DatasetsColumnId
  ).reverse

  val DynTableDef = DynTable(
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

  private def flippedBreakpoint(breakpoint: Breakpoint): Breakpoint =
    breakpoint match
      case Breakpoint.Enabled  => Breakpoint.Disabled
      case Breakpoint.Disabled => Breakpoint.Enabled

  private def column[V](
    id:     ColumnId,
    header: VdomNode,
    cell:   js.UndefOr[
      raw.buildLibCoreCellMod.CellContext[SequenceTableRowType, V] => VdomNode
    ] = js.undefined
  ): ColumnDef[SequenceTableRowType, V] =
    ColDef[V](id, header = _ => header, cell = cell).setColumnSize(ColumnSizes(id))

  protected def columnDefs(flipBreakpoint: (Observation.Id, Step.Id, Breakpoint) => Callback)(
    clientMode:     ClientMode,
    instrument:     Instrument,
    obsId:          Observation.Id,
    requests:       ObservationRequests,
    executionState: ExecutionState,
    progress:       Option[StepProgress],
    isPreview:      Boolean,
    selectedStepId: Option[Step.Id]
  ): List[ColumnDef[SequenceTableRowType, ?]] =
    List(
      SequenceColumns.headerCell(HeaderColumnId, ColDef).setColumnSize(ColumnSizes(HeaderColumnId)),
      column(
        BreakpointColumnId,
        "",
        cell =>
          cell.row.original.value.toOption.map: stepRow =>
            val step: SequenceRow[DynamicConfig] = stepRow.step
            val stepId: Option[Step.Id]          = step.id.toOption
            // val canSetBreakpoint =
            //   clientStatus.canOperate && step.get.canSetBreakpoint(
            //     execution.map(_.steps).orEmpty
            //   )

            <.div(
              <.div(
                ObserveStyles.BreakpointHandle,
                stepId
                  .map: sId =>
                    ^.onClick ==> (_.stopPropagationCB >> flipBreakpoint(
                      obsId,
                      sId,
                      flippedBreakpoint(step.breakpoint)
                    ))
                  .whenDefined
              )(
                Icons.CircleSolid
                  .withFixedWidth()
                  .withClass(
                    ObserveStyles.BreakpointIcon |+|
                      // ObserveStyles.FlippableBreakpoint.when_(canSetBreakpoint) |+|
                      ObserveStyles.ActiveBreakpoint.when_(
                        step.breakpoint === Breakpoint.Enabled
                      )
                  )
              ).when(cell.row.index.toInt > 0 && step.stepTime === StepTime.Present)
            )
      ),
      column(BreakpointSpaceColumnId, "", _ => EmptyVdom),
      column(
        RunningStateColumnId,
        "",
        _.row.original.value.toOption
          .map(_.step)
          .map: step =>
            (step.id.toOption, step.stepTypeDisplay).mapN: (stepId, stepType) =>
              selectedStepId
                .filter(_ === stepId)
                .map: stepId =>
                  StepProgressCell(
                    clientMode = clientMode,
                    instrument = instrument,
                    stepId = stepId,
                    stepType = stepType,
                    isFinished = step.isFinished,
                    obsId = obsId,
                    requests = requests,
                    runningStepId = executionState.runningStepId,
                    sequenceState = executionState.sequenceState,
                    isPausedInStep = executionState.pausedStep.exists(_.value === stepId),
                    subsystemStatus = executionState.stepResources
                      .find(_._1 === stepId)
                      .map(_._2.toMap)
                      .getOrElse(Map.empty),
                    progress = progress,
                    selectedStep = selectedStepId,
                    isPreview = isPreview
                  )
      )
    ) ++
      SequenceColumns
        .gmosColumns(ColDef, _._1.some, _._2.some)
        .map(colDef => colDef.setColumnSize(ColumnSizes(colDef.id))) ++
      List(
        // column(ObsModeColumnId, "Observing Mode"),
        column(CameraColumnId, "Camera"),
        column(DeckerColumnId, "Decker"),
        column(ReadModeColumnId, "ReadMode"),
        column(ImagingMirrorColumnId, "ImagingMirror"),
        column(DatasetsColumnId, "Dataset(s)"),
        column(
          SettingsColumnId,
          Icons.RectangleList,
          cell => SettingsCell() // TODO
        )
      )
