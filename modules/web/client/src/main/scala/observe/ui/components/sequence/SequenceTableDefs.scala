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
import lucuma.react.SizePx
import lucuma.react.common.*
import lucuma.react.syntax.*
import lucuma.react.table.*
import lucuma.ui.sequence.*
import lucuma.ui.sequence.SequenceColumns.*
import lucuma.ui.table.*
import lucuma.ui.table.ColumnSize.*
import lucuma.ui.table.hooks.*
import observe.model.ExecutionState
import observe.model.StepProgress
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.sequence.steps.*
import observe.ui.model.ObservationRequests
import observe.ui.model.enums.ClientMode

import scalajs.js

// Offload SequenceTable definitions to improve legibility.
trait SequenceTableDefs[D] extends SequenceRowBuilder[D]:
  protected case class TableMeta(
    requests:       ObservationRequests,
    executionState: ExecutionState,
    progress:       Option[StepProgress],
    selectedStepId: Option[Step.Id]
  )

  // protected type SequenceTableRowType = Expandable[HeaderOrRow[SequenceTableRow[DynamicConfig]]]
  protected def ColDef = ColumnDef.WithTableMeta[SequenceTableRowType, TableMeta]

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

  protected val ColumnSizes: Map[ColumnId, ColumnSize] = Map(
    HeaderColumnId          -> FixedSize(0.toPx),
    BreakpointColumnId      -> FixedSize(0.toPx),
    BreakpointSpaceColumnId -> FixedSize(30.toPx),
    ExtraRowColumnId        -> FixedSize(0.toPx),
    CameraColumnId          -> Resizable(10.toPx),
    DeckerColumnId          -> Resizable(10.toPx),
    ReadModeColumnId        -> Resizable(180.toPx),
    ImagingMirrorColumnId   -> Resizable(10.toPx),
    SettingsColumnId        -> FixedSize(39.toPx)
  ) ++ SequenceColumns.BaseColumnSizes

  // The order in which they are removed by overflow. The ones at the beginning go first.
  // Missing columns are not removed by overflow. (We declare them in reverse order)
  protected val ColumnPriorities: List[ColumnId] =
    List(
      CameraColumnId,
      DeckerColumnId,
      ReadModeColumnId,
      ImagingMirrorColumnId,
      SettingsColumnId
    ).reverse ++ SequenceColumns.BaseColumnPriorities

  protected val DynTableDef = DynTable(
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
    cell:   js.UndefOr[CellContext[SequenceTableRowType, V, TableMeta, Nothing] => VdomNode] =
      js.undefined
  ): ColumnDef[SequenceTableRowType, V, TableMeta, Nothing] =
    ColDef[V](id, header = _ => header, cell = cell)

  protected def columnDefs(flipBreakpoint: (Observation.Id, Step.Id, Breakpoint) => Callback)(
    clientMode: ClientMode,
    instrument: Instrument,
    obsId:      Observation.Id,
    isPreview:  Boolean
  ): List[ColumnDef[SequenceTableRowType, ?, TableMeta, ?]] =
    List(
      SequenceColumns.headerCell(HeaderColumnId, ColDef).setColumnSize(ColumnSizes(HeaderColumnId)),
      column(
        BreakpointColumnId,
        "",
        cell =>
          cell.row.original.value.toOption.map: stepRow =>
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
        ExtraRowColumnId,
        "",
        cell =>
          cell.table.options.meta.map: meta =>
            cell.row.original.value.toOption
              .map(_.step)
              .map[VdomNode]:
                case step @ SequenceRow.Executed.ExecutedStep(_, _) =>
                  renderVisitExtraRow(step)
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
      SequenceColumns
        .gmosColumns(ColDef, _._1.some, _._2.some)
        .map(colDef => colDef.setColumnSize(ColumnSizes(colDef.id))) ++
      List(
        // column(ObsModeColumnId, "Observing Mode"),
        column(CameraColumnId, "Camera"),
        column(DeckerColumnId, "Decker"),
        column(ReadModeColumnId, "ReadMode"),
        column(ImagingMirrorColumnId, "ImagingMirror"),
        column(
          SettingsColumnId,
          Icons.RectangleList,
          cell => SettingsCell() // TODO
        )
      )
