// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.Eq
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
import lucuma.react.resizeDetector.hooks.*
import lucuma.react.syntax.*
import lucuma.react.table.*
import lucuma.typed.{tanstackTableCore => raw}
import lucuma.ui.reusability.given
import lucuma.ui.sequence.SequenceColumns.*
import lucuma.ui.sequence.*
import lucuma.ui.table.ColumnSize.*
import lucuma.ui.table.*
import lucuma.ui.table.hooks.*
import observe.model.ExecutionState
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.sequence.steps.*
import observe.ui.model.TabOperations
import observe.ui.model.enums.ClientMode
import observe.ui.model.reusability.given

import scalajs.js

sealed trait SequenceTables[S, D](
  protected[sequence] val instrument:    Instrument,
  protected[sequence] val nodAndShuffle: Option[GmosNodAndShuffle]
):
  def clientMode: ClientMode
  def obsId: Observation.Id
  def config: ExecutionConfig[S, D]
  def executionState: ExecutionState
  def tabOperations: TabOperations
  def isPreview: Boolean
  def flipBreakpoint: (Observation.Id, Step.Id, Breakpoint) => Callback

  // TODO: nextAtom will actually come from the observe server.
  private def steps(sequence: ExecutionSequence[D]): List[SequenceRow.FutureStep[D]] =
    SequenceRow.FutureStep.fromAtoms(
      sequence.nextAtom +: sequence.possibleFuture,
      _ => none // TODO Pass signal to noise
    )

  protected[sequence] lazy val acquisitionSteps: List[SequenceRow.FutureStep[D]] =
    config.acquisition.map(steps).orEmpty

  protected[sequence] lazy val scienceSteps: List[SequenceRow.FutureStep[D]] =
    config.science.map(steps).orEmpty

  protected[sequence] lazy val runningStepId: Option[Step.Id] = executionState.runningStepId

case class GmosNorthSequenceTables(
  clientMode:     ClientMode,
  obsId:          Observation.Id,
  config:         ExecutionConfig[StaticConfig.GmosNorth, DynamicConfig.GmosNorth],
  executionState: ExecutionState,
  tabOperations:  TabOperations,
  isPreview:      Boolean,
  flipBreakpoint: (Observation.Id, Step.Id, Breakpoint) => Callback
) extends ReactFnProps(GmosNorthSequenceTables.component)
    with SequenceTables[StaticConfig.GmosNorth, DynamicConfig.GmosNorth](
      Instrument.GmosNorth,
      config.static.nodAndShuffle
    )

case class GmosSouthSequenceTables(
  clientMode:     ClientMode,
  obsId:          Observation.Id,
  config:         ExecutionConfig[StaticConfig.GmosSouth, DynamicConfig.GmosSouth],
  executionState: ExecutionState,
  tabOperations:  TabOperations,
  isPreview:      Boolean,
  flipBreakpoint: (Observation.Id, Step.Id, Breakpoint) => Callback
) extends ReactFnProps(GmosSouthSequenceTables.component)
    with SequenceTables[StaticConfig.GmosSouth, DynamicConfig.GmosSouth](
      Instrument.GmosSouth,
      config.static.nodAndShuffle
    )

private sealed trait SequenceTablesBuilder[S: Eq, D: Eq]:
  private type Props = SequenceTables[S, D]

  private case class SequenceTableRow(step: SequenceRow.FutureStep[D], index: StepIndex)

  private def ColDef = ColumnDef[SequenceTableRow]

  // Breakpoint column has width 0 but is translated and actually shown.
  // We display an empty BreakpointSpace column to show the space with correct borders.
  private val BreakpointColumnId: ColumnId      = ColumnId("breakpoint")
  private val BreakpointSpaceColumnId: ColumnId = ColumnId("breakpointDummy")
  private val RunningStateColumnId: ColumnId    = ColumnId("runningState")
  // private val ObsModeColumnId: ColumnId         = ColumnId("obsMode")
  private val CameraColumnId: ColumnId          = ColumnId("camera")
  private val DeckerColumnId: ColumnId          = ColumnId("decker")
  private val ReadModeColumnId: ColumnId        = ColumnId("readMode")
  private val ImagingMirrorColumnId: ColumnId   = ColumnId("imagingMirror")
  private val DatasetsColumnId: ColumnId        = ColumnId("datasets")
  private val SettingsColumnId: ColumnId        = ColumnId("settings")

  private val ColumnSizes: Map[ColumnId, ColumnSize] = Map(
    BreakpointColumnId      -> FixedSize(0.toPx),
    BreakpointSpaceColumnId -> FixedSize(30.toPx),
    IndexAndTypeColumnId    -> FixedSize(60.toPx),
    RunningStateColumnId    -> FixedSize(0.toPx), // Resizable(380.toPx, min = 380.toPx.some),
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
  private val ColumnPriorities: List[ColumnId] = List(
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

  private def column[V](
    id:     ColumnId,
    header: VdomNode,
    cell:   js.UndefOr[
      raw.buildLibCoreCellMod.CellContext[SequenceTableRow, V] => VdomNode
    ] = js.undefined
  ): ColumnDef[SequenceTableRow, V] =
    ColDef[V](id, header = _ => header, cell = cell).setColumnSize(ColumnSizes(id))

  protected[sequence] val component =
    ScalaFnComponent
      .withHooks[Props]
      .useState(none[Step.Id]) // selectedStep
      .useResizeDetector()
      .useMemoBy((props, selectedStep, _) => // cols
        (props.clientMode,
         props.instrument,
         props.obsId,
         props.tabOperations,
         props.executionState,
         props.isPreview,
         selectedStep.value
        )
      ): (props, _, _) =>
        (clientMode, instrument, obsId, tabOperations, executionState, isPreview, selectedStep) =>
          List(
            column(
              BreakpointColumnId,
              "",
              cell =>
                val stepId: Option[Step.Id] = cell.row.original.step.id.toOption
                // val canSetBreakpoint =
                //   clientStatus.canOperate && step.get.canSetBreakpoint(
                //     execution.map(_.steps).orEmpty
                //   )

                <.div(
                  <.div(
                    ObserveStyles.BreakpointHandle,
                    stepId
                      .map: sId =>
                        ^.onClick --> props.flipBreakpoint(
                          obsId,
                          sId,
                          if (executionState.breakpoints.contains(sId)) Breakpoint.Disabled
                          else Breakpoint.Enabled
                        )
                      .whenDefined
                  )(
                    Icons.CircleSolid
                      .withFixedWidth()
                      .withClass(
                        ObserveStyles.BreakpointIcon |+|
                          // ObserveStyles.FlippableBreakpoint.when_(canSetBreakpoint) |+|
                          ObserveStyles.ActiveBreakpoint.when_(
                            stepId.exists(executionState.breakpoints.contains)
                          )
                      )
                  ).when(cell.row.index.toInt > 0)
                )
            ),
            column(
              BreakpointSpaceColumnId,
              "",
              _ => EmptyVdom
            ),
            column(
              RunningStateColumnId,
              "",
              cell =>
                val step = cell.row.original.step

                (step.id.toOption, step.stepTypeDisplay).mapN: (stepId, stepType) =>
                  props.runningStepId.flatMap: stepId =>
                    Option.when(cell.row.original.step.id.contains(stepId)):
                      StepProgressCell(
                        clientMode = clientMode,
                        instrument = instrument,
                        stepId = stepId,
                        stepType = stepType,
                        isFinished = step.isFinished,
                        stepIndex = cell.row.index.toInt,
                        obsId = obsId,
                        tabOperations = tabOperations,
                        sequenceState = executionState.sequenceState,
                        configStatus = executionState.configStatus,
                        selectedStep = selectedStep,
                        isPreview = isPreview
                      )
            )
          ) ++
            SequenceColumns
              .gmosColumns(ColDef, _.step.some, _.index.some)
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
      .useMemoBy((props, _, _, _) => (props.acquisitionSteps, props.scienceSteps))( // sequences
        (props, _, _, _) =>
          (acquisitionSequence, scienceSequence) =>
            (
              acquisitionSequence.zipWithStepIndex.map(SequenceTableRow.apply),
              scienceSequence.zipWithStepIndex.map(SequenceTableRow.apply)
            )
      )
      .useDynTableBy((_, _, resize, _, _) => (DynTableDef, SizePx(resize.width.orEmpty)))
      .useReactTableBy: (props, _, resize, cols, sequences, dynTable) =>
        TableOptions(
          cols,
          sequences.map(_._1),
          enableSorting = false,
          enableColumnResizing = true,
          columnResizeMode = ColumnResizeMode.OnChange, // Maybe we should use OnEnd here?
          state = PartialTableState(
            columnSizing = dynTable.columnSizing,
            columnVisibility = dynTable.columnVisibility
          ),
          onColumnSizingChange = dynTable.onColumnSizingChangeHandler
        )
      .useReactTableBy: (props, _, resize, cols, sequences, dynTable, _) =>
        TableOptions(
          cols,
          sequences.map(_._2),
          enableSorting = false,
          enableColumnResizing = true,
          columnResizeMode = ColumnResizeMode.OnChange, // Maybe we should use OnEnd here?
          state = PartialTableState(
            columnSizing = dynTable.columnSizing,
            columnVisibility = dynTable.columnVisibility
          ),
          onColumnSizingChange = dynTable.onColumnSizingChangeHandler
        )
      .render: (props, _, resize, _, _, _, acquisitionTable, scienceTable) =>
        extension (row: SequenceTableRow)
          def isRunning: Boolean =
            props.runningStepId match
              case Some(stepId) => row.step.id.contains(stepId)
              case _            => false

        val tableStyle: Css =
          ObserveStyles.ObserveTable |+| ObserveStyles.StepTable |+| SequenceStyles.SequenceTable

        def computeRowClass(row: raw.buildLibTypesMod.Row[SequenceTableRow]): Css =
          val step                       = row.original.step
          // val index                      = row.original.index
          val stepIdOpt: Option[Step.Id] = row.original.step.id.toOption

          (props.runningStepId match
            case Some(stepId) if stepIdOpt.contains(stepId) => ObserveStyles.RowRunning
            case _                                          => ObserveStyles.RowIdle
          ) |+|
            ObserveStyles.StepRowWithBreakpoint.when_(
              stepIdOpt.exists(props.executionState.breakpoints.contains)
            ) |+|
            ObserveStyles.StepRowFirstInAtom.when_(step.firstOf.isDefined) |+|
            (step match
              // case s if s.hasError                       => ObserveStyles.StepRowError
              // case s if s.status === StepState.Running   => ObserveStyles.StepRowRunning
              // case s if s.status === StepState.Paused    => ObserveStyles.StepRowWarning
              // case s if s.status === StepState.Completed => ObserveStyles.StepRowDone
              // case s if s.status === StepState.Aborted   => ObserveStyles.RowError
              // case s if s.isFinished => ObserveStyles.RowDone
              // case _                 => ObserveStyles.StepRow
              case _ => Css.Empty
            )

        def computeHeaderCellClass(
          headerCell: raw.buildLibTypesMod.Header[SequenceTableRow, Any]
        ): Css =
          headerCell.column.id match
            case id if id == BreakpointColumnId.value   => ObserveStyles.BreakpointTableHeader
            case id if id == RunningStateColumnId.value => ObserveStyles.RunningStateTableHeader
            case _                                      => Css.Empty

        def computeCellMods(cell: raw.buildLibTypesMod.Cell[SequenceTableRow, Any]): TagMod =
          cell.column.id match
            case id if id == BreakpointColumnId.value                                  =>
              ObserveStyles.BreakpointTableCell
            case id if id == RunningStateColumnId.value && cell.row.original.isRunning =>
              TagMod(
                ObserveStyles.RunningStateTableCellShown,
                resize.width
                  .map: w =>
                    ^.width := s"${w - ColumnSizes(BreakpointSpaceColumnId).initial.value}px"
                  .whenDefined
              )
            case _                                                                     =>
              TagMod.empty

        val acquisition =
          PrimeTable(
            acquisitionTable,
            tableMod = tableStyle,
            rowMod = computeRowClass,
            headerCellMod = computeHeaderCellClass,
            cellMod = computeCellMods
          )

        PrimeAutoHeightVirtualizedTable(
          scienceTable,
          estimateSize = _ => 25.toPx,
          containerRef = resize.ref,
          tableMod = TagMod(tableStyle, ^.marginTop := "15px"),
          rowMod = computeRowClass,
          // We display the whole acquisition table as a preamble to the science table, which is virtualized.
          // This renders as:
          //  <div outer>
          //    <div inner>
          //      Acquisition Table (complete)
          //      Science Table (virtualized)
          // TODO Test if virtualization scrollbar works well with this approach when there are a lot of rows.
          innerContainerMod = TagMod(^.width := "100%", acquisition),
          headerCellMod = computeHeaderCellClass,
          cellMod = computeCellMods,
          overscan = 5
        )

object GmosNorthSequenceTables
    extends SequenceTablesBuilder[StaticConfig.GmosNorth, DynamicConfig.GmosNorth]

object GmosSouthSequenceTables
    extends SequenceTablesBuilder[StaticConfig.GmosSouth, DynamicConfig.GmosSouth]
