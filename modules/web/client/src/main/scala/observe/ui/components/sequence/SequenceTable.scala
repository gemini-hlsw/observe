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
import lucuma.schemas.model.Visit
import lucuma.typed.tanstackTableCore as raw
import lucuma.ui.reusability.given
import lucuma.ui.sequence.*
import lucuma.ui.table.*
import lucuma.ui.table.hooks.*
import lucuma.ui.react.given
import observe.model.ExecutionState
import observe.model.ObserveStep
import observe.model.StepProgress
import observe.model.StepState
import observe.ui.ObserveStyles
import observe.ui.components.sequence.steps.*
import observe.ui.model.ObservationRequests
import observe.ui.model.enums.ClientMode
import observe.ui.model.reusability.given

sealed trait SequenceTable[S, D <: DynamicConfig](
  protected[sequence] val instrument:    Instrument,
  protected[sequence] val nodAndShuffle: Option[GmosNodAndShuffle]
):
  def clientMode: ClientMode
  def obsId: Observation.Id
  def config: ExecutionConfig[S, D]
  def visits: List[Visit[D]]
  def executionState: ExecutionState
  def progress: Option[StepProgress]
  def selectedStepId: Option[Step.Id]
  def setSelectedStepId: Step.Id => Callback
  def requests: ObservationRequests
  def isPreview: Boolean
  def flipBreakpoint: (Observation.Id, Step.Id, Breakpoint) => Callback

  private def steps(sequence: ExecutionSequence[D]): List[SequenceRow.FutureStep[D]] =
    SequenceRow.FutureStep
      .fromAtoms(
        sequence.possibleFuture,
        _ => none // TODO Pass signal to noise
      )

  protected[sequence] lazy val (acquisitionCurrentSteps, scienceCurrentSteps)
    : (List[ObserveStep], List[ObserveStep]) =
    executionState.sequenceType match
      case SequenceType.Acquisition => (executionState.loadedSteps, List.empty)
      case SequenceType.Science     => (List.empty, executionState.loadedSteps)

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

  protected[sequence] lazy val acquisitionRows: List[SequenceRow[D]] =
    currentStepsToRows(acquisitionCurrentSteps) ++ config.acquisition.map(steps).orEmpty

  protected[sequence] lazy val scienceRows: List[SequenceRow[D]] =
    currentStepsToRows(scienceCurrentSteps) ++ config.science.map(steps).orEmpty

case class GmosNorthSequenceTable(
  clientMode:        ClientMode,
  obsId:             Observation.Id,
  config:            ExecutionConfig[StaticConfig.GmosNorth, DynamicConfig.GmosNorth],
  visits:            List[Visit.GmosNorth],
  executionState:    ExecutionState,
  progress:          Option[StepProgress],
  selectedStepId:    Option[Step.Id],
  setSelectedStepId: Step.Id => Callback,
  requests:          ObservationRequests,
  isPreview:         Boolean,
  flipBreakpoint:    (Observation.Id, Step.Id, Breakpoint) => Callback
) extends ReactFnProps(GmosNorthSequenceTable.component)
    with SequenceTable[StaticConfig.GmosNorth, DynamicConfig.GmosNorth](
      Instrument.GmosNorth,
      config.static.nodAndShuffle
    )

case class GmosSouthSequenceTable(
  clientMode:        ClientMode,
  obsId:             Observation.Id,
  config:            ExecutionConfig[StaticConfig.GmosSouth, DynamicConfig.GmosSouth],
  visits:            List[Visit.GmosSouth],
  executionState:    ExecutionState,
  progress:          Option[StepProgress],
  selectedStepId:    Option[Step.Id],
  setSelectedStepId: Step.Id => Callback,
  requests:          ObservationRequests,
  isPreview:         Boolean,
  flipBreakpoint:    (Observation.Id, Step.Id, Breakpoint) => Callback
) extends ReactFnProps(GmosSouthSequenceTable.component)
    with SequenceTable[StaticConfig.GmosSouth, DynamicConfig.GmosSouth](
      Instrument.GmosSouth,
      config.static.nodAndShuffle
    )

private sealed trait SequenceTableBuilder[S: Eq, D <: DynamicConfig: Eq] extends SequenceTableDefs[D]:
  private type Props = SequenceTable[S, D]

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
        columnDefs(props.flipBreakpoint)
      .useMemoBy((props, _, _) => props.visits): (_, _, _) =>
        visitsSequences // (List[Visit], nextIndex)
      .useMemoBy((props, _, _, visitsData) =>
        (visitsData, props.acquisitionRows, props.scienceRows)
      ): (_, _, _, _) =>
        (visitsData, acquisitionSteps, scienceSteps) =>
          val (visits, nextIndex): (List[VisitData], StepIndex) = visitsData.value
          stitchSequence(visits, nextIndex, acquisitionSteps,  scienceSteps)
      .useDynTableBy: (_, resize, _, _, _) => 
        (DynTableDef, SizePx(resize.width.orEmpty))
      .useReactTableBy: (props, _, cols, _, sequence, dynTable) =>
        TableOptions(
          cols.map(dynTable.setInitialColWidths),
          sequence,
          enableSorting = false,
          // TODO We are having trouble with reisizable cols, will be addressed in future PR
          // enableColumnResizing = true,
          enableColumnResizing = false,
          enableExpanding = true,
          getRowId = (row, _, _) => getRowId(row),
          getSubRows = (row, _) => row.subRows,
          columnResizeMode = ColumnResizeMode.OnChange,
          initialState = TableState(
            expanded = CurrentExpandedState
          ),
          state = PartialTableState(
            columnSizing = dynTable.columnSizing,
            columnVisibility = dynTable.columnVisibility
          ),
          // onColumnSizingChange = dynTable.onColumnSizingChangeHandler
        )
      .render: (props, resize, cols, _, _, _, table) =>
        extension (step: SequenceRow[DynamicConfig])
          def isSelected: Boolean =
            props.selectedStepId match
              case Some(stepId) => step.id.contains(stepId)
              case _            => false

        val tableStyle: Css =
          ObserveStyles.ObserveTable |+| ObserveStyles.StepTable |+| SequenceStyles.SequenceTable

        def computeRowMods(row: SequenceTableRowType): TagMod =
          row.value.toOption
            .map(_.step)
            .map: step =>
              val stepIdOpt: Option[Step.Id] = step.id.toOption

              TagMod(
                stepIdOpt
                  .map: stepId =>
                    (^.onClick --> props.setSelectedStepId(stepId))
                      .when:
                        step.stepTime === StepTime.Present
                      .unless:
                        props.executionState.isLocked
                  .whenDefined,
                if (step.isSelected) SequenceStyles.RowHasExtra else ObserveStyles.RowIdle,
                step match
                  case SequenceRow.Executed.ExecutedStep(_, _) => SequenceStyles.RowHasExtra
                  case _ => TagMod.empty,
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
                  case _                                    => TagMod.empty
              )
            .orEmpty

        def computeHeaderCellMods(
          headerCell: raw.buildLibTypesMod.Header[SequenceTableRowType, Any]
        ): TagMod =
          headerCell.column.id match
            case id if id == HeaderColumnId.value     => SequenceStyles.HiddenColTableHeader
            case id if id == BreakpointColumnId.value => SequenceStyles.HiddenColTableHeader
            case id if id == ExtraRowColumnId.value   => SequenceStyles.HiddenColTableHeader
            case _                                    => TagMod.empty

        val extraRowMod: TagMod =
          TagMod(
            SequenceStyles.ExtraRowShown,
            resize.width
              .map: w =>
                ^.width := s"${w - ColumnSizes(BreakpointSpaceColumnId).initial.value}px"
              .whenDefined
          )

        def computeCellMods(cell: raw.buildLibTypesMod.Cell[SequenceTableRowType, Any]): TagMod =
          cell.row.original.value match
            case Left(_)        => // Header
              cell.column.id match
                case id if id == HeaderColumnId.value => TagMod(^.colSpan := cols.length)
                case _                                => ^.display.none
            case Right(stepRow) =>
              cell.column.id match
                case id if id == HeaderColumnId.value     => // TODO MOVE TO STYLE
                  TagMod(^.border := "0px", ^.padding := "0px")
                case id if id == BreakpointColumnId.value =>
                  ObserveStyles.BreakpointTableCell
                case id if id == ExtraRowColumnId.value   =>
                  stepRow.step match // Extra row is shown in a selected row or in an executed step row.
                    case SequenceRow.Executed.ExecutedStep(_, _) => extraRowMod
                    case step if step.isSelected                 => extraRowMod
                    case _                                       => TagMod.empty
                case _                                    =>
                  TagMod.empty

        PrimeAutoHeightVirtualizedTable(
          table,
          estimateSize = _ => 25.toPx,
          overscan = 8,
          containerRef = resize.ref,
          tableMod = TagMod(tableStyle),
          rowMod = row => computeRowMods(row.original),
          headerCellMod = computeHeaderCellMods,
          cellMod = computeCellMods
        )

object GmosNorthSequenceTable
    extends SequenceTableBuilder[StaticConfig.GmosNorth, DynamicConfig.GmosNorth]

object GmosSouthSequenceTable
    extends SequenceTableBuilder[StaticConfig.GmosSouth, DynamicConfig.GmosSouth]
