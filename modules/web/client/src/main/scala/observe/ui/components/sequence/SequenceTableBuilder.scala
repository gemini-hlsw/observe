// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.effect.IO
import cats.syntax.all.*
import crystal.react.hooks.*
import crystal.react.syntax.effect.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.hooks.Hooks.UseRef
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.model.sequence.*
import lucuma.react.SizePx
import lucuma.react.common.*
import lucuma.react.primereact.Button
import lucuma.react.resizeDetector.hooks.*
import lucuma.react.table.*
import lucuma.schemas.model.enums.StepExecutionState
import lucuma.typed.tanstackVirtualCore as rawVirtual
import lucuma.ui.primereact.*
import lucuma.ui.react.given
import lucuma.ui.reusability.given
import lucuma.ui.sequence.*
import lucuma.ui.table.*
import lucuma.ui.table.hooks.*
import observe.model.SequenceState
import observe.model.StepState
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.sequence.steps.*
import observe.ui.model.AppContext
import observe.ui.model.reusability.given
import observe.ui.services.ODBQueryApi
import observe.ui.services.SequenceApi
import org.typelevel.log4cats.Logger

import scala.scalajs.LinkingInfo

// Helper to build component objects for instrument sequence tables.
private trait SequenceTableBuilder[S, D](protected val instrument: Instrument)
    extends SequenceTableDefs[D]:
  private type Props = SequenceTable[S, D]

  private val ScrollOptions =
    rawVirtual.mod
      .ScrollToOptions()
      .setBehavior(rawVirtual.mod.ScrollBehavior.smooth)
      .setAlign(rawVirtual.mod.ScrollAlignment.start)

  private def scrollToRowId(
    virtualizerRef: UseRef[Option[HTMLTableVirtualizer]],
    table:          Table[SequenceTableRowType, TableMeta, Nothing, Nothing]
  )(rowIdCandidates: List[String]): Callback =
    rowIdCandidates.some
      .filter(_.nonEmpty)
      .flatMap: rowIds =>
        table
          .getRowModel()
          .rows
          .indexWhere(row => rowIds.contains_(row.id.value))
          .some
          .filter(_ >= 0)
      .foldMap: rowIndex =>
        virtualizerRef.get.flatMap: refOpt =>
          Callback: // Auto scroll to running step or next step.
            refOpt.map:
              _.scrollToIndex(rowIndex - 1, ScrollOptions)

  protected[sequence] val component =
    ScalaFnComponent[Props]: props =>
      for
        resize                   <- useResizeDetector
        ctx                      <- useContext(AppContext.ctx)
        sequenceApi              <- useContext(SequenceApi.ctx)
        given Logger[IO]          = ctx.logger
        cols                     <-
          useMemo((props.clientMode, props.instrument, props.obsId, props.isPreview)):
            columnDefs(_, _, _, _)
        visitsData               <-
          useMemo((props.visits, props.currentRecordedStepId)):
            visitsSequences
        visits                    = visitsData.map(_._1)
        nextScienceIndex          = visitsData.map(_._2)
        acquisitionPromptClicked <- useStateViewWithReuse(none[SequenceType])
        _                        <-
          useEffectWithDeps(props.executionState.isWaitingAcquisitionPrompt): _ =>
            acquisitionPromptClicked.set(none)
        sequence                 <-
          useMemo(
            (visits,
             nextScienceIndex,
             props.acquisitionRows,
             props.scienceRows,
             props.currentRecordedVisit.map(_.visitId),
             props.executionState.breakpoints,
             props.executionState.sequenceType,
             props.executionState.isWaitingAcquisitionPrompt,
             props.alertPosition,
             props.requests.acquisitionPrompt,
             acquisitionPromptClicked
            )
          ):
            (
              visits,
              nextScienceIndex,
              acquisitionSteps,
              scienceSteps,
              currentVisitId,
              _,
              sequenceType,
              isWaitingAcquisitionPrompt,
              alertPosition,
              acquisitionPromptRequest,
              acquisitionPromptClicked
            ) =>
              val acquisitionPrompt: Option[AlertRow] =
                Option.when(isWaitingAcquisitionPrompt)(
                  AlertRow(
                    sequenceType,
                    alertPosition,
                    AcquisitionPrompt(
                      sequenceApi
                        .loadNextAtom(props.obsId, SequenceType.Science)
                        .runAsync,
                      sequenceApi
                        .loadNextAtom(props.obsId, SequenceType.Acquisition)
                        .runAsync,
                      acquisitionPromptRequest,
                      acquisitionPromptClicked
                    )
                  )
                )

              stitchSequence(
                visits,
                currentVisitId,
                nextScienceIndex,
                acquisitionSteps,
                scienceSteps,
                acquisitionPrompt
              )
        dynTable                 <- useDynTable(DynTableDef, SizePx(resize.width.orEmpty))
        table                    <-
          useReactTable:
            TableOptions(
              cols.map(dynTable.setInitialColWidths),
              sequence,
              enableColumnResizing = true,
              enableExpanding = true,
              getRowId = (row, _, _) => getRowId(row),
              getSubRows = (row, _) => row.subRows,
              columnResizeMode = ColumnResizeMode.OnChange,
              initialState = TableState(expanded = CurrentExpandedState),
              state = PartialTableState(
                columnSizing = dynTable.columnSizing,
                columnVisibility = dynTable.columnVisibility
              ),
              onColumnSizingChange = dynTable.onColumnSizingChangeHandler,
              meta = TableMeta(
                props.requests,
                props.executionState,
                props.progress,
                props.selectedStepId,
                props.datasetIdsInFlight,
                props.onBreakpointFlip,
                props.onDatasetQaChange
              )
            )
        odbQueryApi              <- useContext(ODBQueryApi.ctx)
        virtualizerRef           <- useRef(none[HTMLTableVirtualizer])
        _                        <-
          useEffectOnMount: // If sequence is not running, auto select next step.
            val autoScrollCandidates: List[String] =
              AlertRowId.toString +:
                (props.runningStepId ++ props.nextStepId).map(_.toString).toList

            Callback.when(props.runningStepId.isEmpty)(
              props.nextStepId.map(props.setSelectedStepId(_)).orEmpty
            ) >>
              scrollToRowId(virtualizerRef, table)(autoScrollCandidates)
                .delayMs(1) // https://github.com/TanStack/virtual/issues/615
                .toCallback
        _                        <-
          // When sequence changes state or step, auto scroll to running step.
          useEffectWithDeps(
            (props.executionState.sequenceState.isRunning,
             props.executionState.sequenceState.isWaitingUserPrompt,
             props.runningStepId
            )
          ): (_, _, runningStepId) =>
            val autoScrollCandidates: List[String] =
              AlertRowId.toString +: runningStepId.map(_.toString).toList

            scrollToRowId(virtualizerRef, table)(autoScrollCandidates)
        _                        <- // If sequence completes, expand last visit.
          useEffectWithDeps(props.executionState.sequenceState):
            case SequenceState.Completed =>
              table.modExpanded:
                case Expanded.AllRows    => Expanded.AllRows
                case Expanded.Rows(rows) => Expanded.Rows(rows + (getRowId(sequence.last) -> true))
            case _                       => Callback.empty
      yield
        extension (step: SequenceRow[D])
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
              val stepHasBreakpoint: Boolean =
                stepIdOpt.exists(props.executionState.breakpoints.contains_)

              TagMod(
                stepIdOpt
                  .map: stepId =>
                    TagMod(
                      // Only in dev mode, show step id on hover.
                      if (LinkingInfo.developmentMode) {
                        val executionState: Option[StepExecutionState] = step match
                          case SequenceRow.Executed.ExecutedStep(stepRecord, _) =>
                            stepRecord.executionState.some
                          case _                                                =>
                            none
                        ^.title := stepId.toString + executionState.fold("")(es => s" ($es)")
                      } else TagMod.empty,
                      (^.onClick --> props.setSelectedStepId(stepId))
                        .when:
                          step.stepTime === StepTime.Present
                        .unless:
                          props.executionState.isLocked
                    )
                  .whenDefined,
                SequenceStyles.RowHasExtra.when_(step.isSelected || step.isFinished),
                ObserveStyles.RowIdle.unless_(step.isSelected),
                ObserveStyles.StepRowWithBreakpoint.when_(stepHasBreakpoint),
                ObserveStyles.StepRowFirstInAtom.when_(step.isFirstInAtom),
                ObserveStyles.StepRowPossibleFuture.when_(step.stepTime === StepTime.Future),
                step.stepState match
                  case s if s.hasError                      => ObserveStyles.StepRowError
                  case StepState.Paused                     => ObserveStyles.StepRowWarning
                  case StepState.Completed                  => ObserveStyles.StepRowDone
                  case StepState.Aborted if step.isFinished => ObserveStyles.StepRowError
                  case _                                    => TagMod.empty
              )
            .orEmpty

        def computeHeaderCellMods(
          headerCell: Header[SequenceTableRowType, ?, TableMeta, ?, ?, ?, ?]
        ): TagMod =
          headerCell.column.id match
            case id if id == HeaderColumnId     => SequenceStyles.HiddenColTableHeader
            case id if id == BreakpointColumnId => SequenceStyles.HiddenColTableHeader
            case id if id == ExtraRowColumnId   => SequenceStyles.HiddenColTableHeader
            case _                              => TagMod.empty

        val extraRowMod: TagMod =
          TagMod(
            SequenceStyles.ExtraRowShown,
            resize.width
              .map: w =>
                ^.width := s"${w - ColumnSizes(BreakpointSpaceColumnId).initial.value}px"
              .whenDefined
          )

        def computeCellMods(
          cell: Cell[SequenceTableRowType, ?, TableMeta, ?, ?, ?, ?]
        ): TagMod =
          cell.row.original.value match
            case Left(_)        => // Header
              cell.column.id match
                case id if id == HeaderColumnId => TagMod(^.colSpan := cols.length)
                case _                          => ^.display.none
            case Right(stepRow) =>
              cell.column.id match
                case id if id == HeaderColumnId     => // TODO MOVE TO STYLE
                  TagMod(^.border := "0px", ^.padding := "0px")
                case id if id == BreakpointColumnId =>
                  ObserveStyles.BreakpointTableCell
                case id if id == ExtraRowColumnId   =>
                  stepRow.step match // Extra row is shown in a selected row or in an executed step row.
                    case SequenceRow.Executed.ExecutedStep(_, _) => extraRowMod
                    case step if step.isSelected                 => extraRowMod
                    case _                                       => TagMod.empty
                case _                              =>
                  TagMod.empty

        val visitIds: Set[RowId]     = visits.value.map(_.rowId).toSet
        val collapseVisits: Callback = table.modExpanded:
          case Expanded.AllRows    => Expanded.fromCollapsedRows(visitIds.toList*)
          case Expanded.Rows(rows) => Expanded.Rows(rows ++ (visitIds.map(_ -> false)))
        val expandVisits: Callback   = table.modExpanded:
          case Expanded.AllRows    => Expanded.AllRows
          case Expanded.Rows(rows) => Expanded.Rows(rows ++ (visitIds.map(_ -> true)))

        val rows: List[Row[SequenceTableRowType, TableMeta, Nothing, Nothing]] =
          table.getExpandedRowModel().rows

        def forAllVisits(
          onContains: Row[SequenceTableRowType, TableMeta, Nothing, Nothing] => Boolean
        ): Boolean =
          rows.forall: row =>
            if visitIds.contains(row.id) then onContains(row) else true

        val allVisitsAreCollapsed: Boolean = forAllVisits(!_.getIsExpanded())
        val allVisitsAreExpanded: Boolean  = forAllVisits(_.getIsExpanded())

        def estimateRowHeight(index: Int): SizePx =
          table.getRowModel().rows.get(index).map(_.original.value) match
            case Some(Right(SequenceIndexedRow(CurrentAtomStepRow(_, _, _, _), _)))          =>
              SequenceRowHeight.WithExtra
            case Some(Right(SequenceIndexedRow(SequenceRow.Executed.ExecutedStep(_, _), _))) =>
              SequenceRowHeight.WithExtra
            case _                                                                           =>
              SequenceRowHeight.Regular

        React.Fragment(
          if (visitIds.nonEmpty) {
            <.div(ObserveStyles.SequenceTableExpandButton)(
              Button(
                icon = Icons.Minus,
                label = "Collapse all visits",
                disabled = allVisitsAreCollapsed,
                onClick = collapseVisits
              ).mini.compact,
              Button(
                icon = Icons.Plus,
                label = "Expand all visits",
                disabled = allVisitsAreExpanded,
                onClick = expandVisits
              ).mini.compact
            )
          } else EmptyVdom,
          PrimeAutoHeightVirtualizedTable(
            table,
            estimateSize = estimateRowHeight,
            overscan = 8,
            containerRef = resize.ref,
            virtualizerRef = virtualizerRef,
            tableMod = TagMod(tableStyle),
            rowMod = row => computeRowMods(row.original),
            headerCellMod = computeHeaderCellMods,
            cellMod = computeCellMods
          )
        )
