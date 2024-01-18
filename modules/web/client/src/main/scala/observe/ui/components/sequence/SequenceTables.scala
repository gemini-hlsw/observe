// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.Eq
import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
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
import observe.model.ObserveStep
import observe.model.StepProgress
import observe.model.StepState
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.sequence.steps.*
import observe.ui.model.ObservationRequests
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
  def progress: Option[StepProgress]
  def selectedStepId: Option[Step.Id]
  def setSelectedStepId: Step.Id => Callback
  def requests: ObservationRequests
  def isPreview: Boolean
  def flipBreakpoint: (Observation.Id, Step.Id, Breakpoint) => Callback

  private def steps(sequence: ExecutionSequence[D]): List[SequenceRow.FutureStep[DynamicConfig]] =
    SequenceRow.FutureStep
      .fromAtoms(
        sequence.possibleFuture,
        _ => none // TODO Pass signal to noise
      )
      .map(_.asInstanceOf[SequenceRow.FutureStep[DynamicConfig]]) // ObserveStep loses the type

  protected[sequence] lazy val (acquisitionCurrentSteps, scienceCurrentSteps)
    : (List[ObserveStep], List[ObserveStep]) =
    executionState.sequenceType match
      case SequenceType.Acquisition => (executionState.loadedSteps, List.empty)
      case SequenceType.Science     => (List.empty, executionState.loadedSteps)

  protected[sequence] def currentStepsToRows(
    currentSteps: List[ObserveStep]
  ): List[CurrentAtomStepRow] =
    currentSteps.map: step =>
      CurrentAtomStepRow(
        step,
        breakpoint =
          if (executionState.breakpoints.contains_(step.id)) Breakpoint.Enabled
          else Breakpoint.Disabled,
        isFirstOfAtom = currentSteps.headOption.exists(_.id === step.id)
      )

  // TODO Stitch past visits
  protected[sequence] lazy val acquisitionRows: List[SequenceRow[DynamicConfig]] =
    currentStepsToRows(acquisitionCurrentSteps) ++ config.acquisition.map(steps).orEmpty

  protected[sequence] lazy val scienceRows: List[SequenceRow[DynamicConfig]] =
    currentStepsToRows(scienceCurrentSteps) ++ config.science.map(steps).orEmpty

case class GmosNorthSequenceTables(
  clientMode:        ClientMode,
  obsId:             Observation.Id,
  config:            ExecutionConfig[StaticConfig.GmosNorth, DynamicConfig.GmosNorth],
  executionState:    ExecutionState,
  progress:          Option[StepProgress],
  selectedStepId:    Option[Step.Id],
  setSelectedStepId: Step.Id => Callback,
  requests:          ObservationRequests,
  isPreview:         Boolean,
  flipBreakpoint:    (Observation.Id, Step.Id, Breakpoint) => Callback
) extends ReactFnProps(GmosNorthSequenceTables.component)
    with SequenceTables[StaticConfig.GmosNorth, DynamicConfig.GmosNorth](
      Instrument.GmosNorth,
      config.static.nodAndShuffle
    )

case class GmosSouthSequenceTables(
  clientMode:        ClientMode,
  obsId:             Observation.Id,
  config:            ExecutionConfig[StaticConfig.GmosSouth, DynamicConfig.GmosSouth],
  executionState:    ExecutionState,
  progress:          Option[StepProgress],
  selectedStepId:    Option[Step.Id],
  setSelectedStepId: Step.Id => Callback,
  requests:          ObservationRequests,
  isPreview:         Boolean,
  flipBreakpoint:    (Observation.Id, Step.Id, Breakpoint) => Callback
) extends ReactFnProps(GmosSouthSequenceTables.component)
    with SequenceTables[StaticConfig.GmosSouth, DynamicConfig.GmosSouth](
      Instrument.GmosSouth,
      config.static.nodAndShuffle
    )

private sealed trait SequenceTablesBuilder[S: Eq, D: Eq]:
  private type Props = SequenceTables[S, D]

  private case class SequenceTableRow(step: SequenceRow[DynamicConfig], index: StepIndex)

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

  private def flipBreakpoint(breakpoint: Breakpoint): Breakpoint =
    breakpoint match
      case Breakpoint.Enabled  => Breakpoint.Disabled
      case Breakpoint.Disabled => Breakpoint.Enabled

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
      .useResizeDetector()
      .useMemoBy((props, _) => // cols
        (props.clientMode,
         props.instrument,
         props.obsId,
         props.requests,
         props.executionState,
         props.progress,
         props.isPreview,
         props.selectedStepId
        )
      ): (props, _) =>
        (
          clientMode,
          instrument,
          obsId,
          requests,
          executionState,
          progress,
          isPreview,
          selectedStepId
        ) =>
          List(
            column(
              BreakpointColumnId,
              "",
              cell =>
                val step: SequenceRow[DynamicConfig] = cell.row.original.step
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
                        ^.onClick ==> (_.stopPropagationCB >> props.flipBreakpoint(
                          obsId,
                          sId,
                          flipBreakpoint(step.breakpoint)
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
                  props.selectedStepId
                    .filter(_ === stepId)
                    .map: stepId =>
                      StepProgressCell(
                        clientMode = clientMode,
                        instrument = instrument,
                        stepId = stepId,
                        stepType = stepType,
                        isFinished = step.isFinished,
                        stepIndex = cell.row.index.toInt,
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
      .useMemoBy((props, _, _) => (props.acquisitionRows, props.scienceRows))( // sequences
        (props, _, _) =>
          (acquisitionRows, scienceRows) =>
            (
              acquisitionRows.zipWithStepIndex.map(SequenceTableRow.apply),
              scienceRows.zipWithStepIndex.map(SequenceTableRow.apply)
            )
      )
      .useDynTableBy((_, resize, _, _) => (DynTableDef, SizePx(resize.width.orEmpty)))
      .useReactTableBy: (props, resize, cols, sequences, dynTable) =>
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
      .useReactTableBy: (props, resize, cols, sequences, dynTable, _) =>
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
      .render: (props, resize, _, _, _, acquisitionTable, scienceTable) =>
        extension (row: SequenceTableRow)
          def isSelected: Boolean =
            props.selectedStepId match
              case Some(stepId) => row.step.id.contains(stepId)
              case _            => false

        val tableStyle: Css =
          ObserveStyles.ObserveTable |+| ObserveStyles.StepTable |+| SequenceStyles.SequenceTable

        def computeRowMods(row: raw.buildLibTypesMod.Row[SequenceTableRow]): TagMod =
          val tableRow                   = row.original
          val step                       = tableRow.step
          val stepIdOpt: Option[Step.Id] = step.id.toOption

          TagMod(
            stepIdOpt
              .map: stepId =>
                (^.onClick --> props.setSelectedStepId(stepId))
                  .when(step.stepTime === StepTime.Present)
                  .unless(props.executionState.isLocked)
              .whenDefined,
            if (tableRow.isSelected) ObserveStyles.RowSelected else ObserveStyles.RowIdle,
            ObserveStyles.StepRowWithBreakpoint.when_(
              stepIdOpt.exists(props.executionState.breakpoints.contains)
            ),
            ObserveStyles.StepRowFirstInAtom.when_(step.isFirstInAtom),
            ObserveStyles.StepRowPossibleFuture.when_(step.stepTime === StepTime.Future),
            step.stepState match
              case s if s.hasError                      => ObserveStyles.StepRowError
              case StepState.Paused | StepState.Skipped => ObserveStyles.StepRowWarning
              case StepState.Completed                  => ObserveStyles.StepRowDone
              case StepState.Aborted                    => ObserveStyles.StepRowError
              case _                                    => Css.Empty
          )

        def computeHeaderCellMods(
          headerCell: raw.buildLibTypesMod.Header[SequenceTableRow, Any]
        ): Css =
          headerCell.column.id match
            case id if id == BreakpointColumnId.value   => ObserveStyles.BreakpointTableHeader
            case id if id == RunningStateColumnId.value => ObserveStyles.RunningStateTableHeader
            case _                                      => Css.Empty

        def computeCellMods(cell: raw.buildLibTypesMod.Cell[SequenceTableRow, Any]): TagMod =
          cell.column.id match
            case id if id == BreakpointColumnId.value                                   =>
              ObserveStyles.BreakpointTableCell
            case id if id == RunningStateColumnId.value && cell.row.original.isSelected =>
              TagMod(
                ObserveStyles.SelectedStateTableCellShown,
                resize.width
                  .map: w =>
                    ^.width := s"${w - ColumnSizes(BreakpointSpaceColumnId).initial.value}px"
                  .whenDefined
              )
            case _                                                                      =>
              TagMod.empty

        val acquisition =
          PrimeTable(
            acquisitionTable,
            tableMod = tableStyle,
            rowMod = computeRowMods,
            headerCellMod = computeHeaderCellMods,
            cellMod = computeCellMods
          )

        PrimeAutoHeightVirtualizedTable(
          scienceTable,
          estimateSize = _ => 25.toPx,
          containerRef = resize.ref,
          tableMod = TagMod(tableStyle, ^.marginTop := "15px"),
          rowMod = computeRowMods,
          // We display the whole acquisition table as a preamble to the science table, which is virtualized.
          // This renders as:
          //  <div outer>
          //    <div inner>
          //      Acquisition Table (complete)
          //      Science Table (virtualized)
          // TODO Test if virtualization scrollbar works well with this approach when there are a lot of rows.
          innerContainerMod = TagMod(^.width := "100%", acquisition),
          headerCellMod = computeHeaderCellMods,
          cellMod = computeCellMods,
          overscan = 5
        )

object GmosNorthSequenceTables
    extends SequenceTablesBuilder[StaticConfig.GmosNorth, DynamicConfig.GmosNorth]

object GmosSouthSequenceTables
    extends SequenceTablesBuilder[StaticConfig.GmosSouth, DynamicConfig.GmosSouth]
