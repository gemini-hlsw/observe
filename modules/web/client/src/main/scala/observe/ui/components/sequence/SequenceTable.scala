// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.Eq
import cats.syntax.all.*
import crystal.react.hooks.*
import crystal.react.syntax.effect.*
import eu.timepit.refined.types.numeric.NonNegInt
import japgolly.scalajs.react.*
import japgolly.scalajs.react.hooks.Hooks.UseRef
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Breakpoint
import lucuma.core.enums.Instrument
import lucuma.core.enums.SequenceType
import lucuma.core.model.Observation
import lucuma.core.model.sequence.*
import lucuma.core.model.sequence.gmos.*
import lucuma.react.SizePx
import lucuma.react.common.*
import lucuma.react.primereact.Button
import lucuma.react.resizeDetector.hooks.*
import lucuma.react.table.*
import lucuma.schemas.model.Visit
import lucuma.typed.tanstackVirtualCore as rawVirtual
import lucuma.ui.primereact.*
import lucuma.ui.react.given
import lucuma.ui.reusability.given
import lucuma.ui.sequence.*
import lucuma.ui.table.*
import lucuma.ui.table.hooks.*
import observe.model.ExecutionState
import observe.model.ObserveStep
import observe.model.StepProgress
import observe.model.StepState
import observe.model.odb.RecordedVisit
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.sequence.steps.*
import observe.ui.model.AppContext
import observe.ui.model.EditableQaFields
import observe.ui.model.ObservationRequests
import observe.ui.model.enums.ClientMode
import observe.ui.model.reusability.given
import observe.ui.services.ODBQueryApi
import observe.ui.services.SequenceApi

import scala.scalajs.LinkingInfo

sealed trait SequenceTable[S, D <: DynamicConfig](
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

  protected[sequence] lazy val acquisitionRows: List[SequenceRow[D]] =
    // If initial acquisition atom is complete, then nextAtom already shows the next potential step. We want to hide that.
    if executionState.isWaitingAcquisitionPrompt || executionState.sequenceType === SequenceType.Science
    then List.empty
    else currentAcquisitionRows

  protected[sequence] lazy val scienceRows: List[SequenceRow[D]] =
    currentScienceRows ++ config.science.map(s => futureSteps(s.possibleFuture)).orEmpty

  // Alert position is right after currently executing atom.
  protected[sequence] lazy val alertPosition: NonNegInt =
    NonNegInt.unsafeFrom(currentAtomPendingSteps.length)

  protected[sequence] lazy val runningStepId: Option[Step.Id] = executionState.runningStepId

  protected[sequence] lazy val nextStepId: Option[Step.Id] =
    currentAtomPendingSteps.headOption.map(_.id)

case class GmosNorthSequenceTable(
  clientMode:           ClientMode,
  obsId:                Observation.Id,
  config:               ExecutionConfig[StaticConfig.GmosNorth, DynamicConfig.GmosNorth],
  visits:               List[Visit.GmosNorth],
  executionState:       ExecutionState,
  currentRecordedVisit: Option[RecordedVisit],
  progress:             Option[StepProgress],
  selectedStepId:       Option[Step.Id],
  setSelectedStepId:    Step.Id => Callback,
  requests:             ObservationRequests,
  isPreview:            Boolean,
  onBreakpointFlip:     (Observation.Id, Step.Id, Breakpoint) => Callback,
  onDatasetQaChange:    Dataset.Id => EditableQaFields => Callback,
  datasetIdsInFlight:   Set[Dataset.Id]
) extends ReactFnProps(GmosNorthSequenceTable.component)
    with SequenceTable[StaticConfig.GmosNorth, DynamicConfig.GmosNorth](
      Instrument.GmosNorth,
      config.static.nodAndShuffle
    )

case class GmosSouthSequenceTable(
  clientMode:           ClientMode,
  obsId:                Observation.Id,
  config:               ExecutionConfig[StaticConfig.GmosSouth, DynamicConfig.GmosSouth],
  visits:               List[Visit.GmosSouth],
  executionState:       ExecutionState,
  currentRecordedVisit: Option[RecordedVisit],
  progress:             Option[StepProgress],
  selectedStepId:       Option[Step.Id],
  setSelectedStepId:    Step.Id => Callback,
  requests:             ObservationRequests,
  isPreview:            Boolean,
  onBreakpointFlip:     (Observation.Id, Step.Id, Breakpoint) => Callback,
  onDatasetQaChange:    Dataset.Id => EditableQaFields => Callback,
  datasetIdsInFlight:   Set[Dataset.Id]
) extends ReactFnProps(GmosSouthSequenceTable.component)
    with SequenceTable[StaticConfig.GmosSouth, DynamicConfig.GmosSouth](
      Instrument.GmosSouth,
      config.static.nodAndShuffle
    )

private sealed trait SequenceTableBuilder[S: Eq, D <: DynamicConfig: Eq]
    extends SequenceTableDefs[D]:
  private type Props = SequenceTable[S, D]

  private val ScrollOptions =
    rawVirtual.mod
      .ScrollToOptions()
      .setBehavior(rawVirtual.mod.ScrollBehavior.smooth)
      .setAlign(rawVirtual.mod.ScrollAlignment.start)

  private def scrollToRowId(
    virtualizerRef: UseRef[Option[HTMLTableVirtualizer]],
    table:          Table[SequenceTableRowType, TableMeta]
  )(rowIdCandidates: List[String]): Callback =
    virtualizerRef.get.flatMap: refOpt =>
      Callback( // Auto scroll to running step or next step.
        refOpt.map:
          _.scrollToIndex(
            table
              .getRowModel()
              .rows
              .indexWhere(row => rowIdCandidates.contains_(row.id.value)) - 1,
            ScrollOptions
          )
      )

  protected[sequence] val component =
    ScalaFnComponent
      .withHooks[Props]
      .useResizeDetector()
      .useContext(AppContext.ctx)
      .useContext(SequenceApi.ctx)
      .useMemoBy((props, _, _, _) => // cols
        (props.clientMode, props.instrument, props.obsId, props.isPreview)
      ): (props, _, ctx, _) =>
        (clientMode, instrument, obsId, isPreview) =>
          import ctx.given

          columnDefs(ctx.httpClient)(props.onBreakpointFlip, props.onDatasetQaChange)(
            clientMode,
            instrument,
            obsId,
            isPreview
          )
      .useMemoBy((props, _, _, _, _) => (props.visits, props.currentRecordedStepId)):
        (_, _, _, _, _) => visitsSequences // (List[Visit], nextIndex)
      .useStateViewWithReuse(none[SequenceType]) // acquisitionPromptClicked
      .useEffectWithDepsBy((props, _, _, _, _, _, _) =>
        props.executionState.isWaitingAcquisitionPrompt
      ): (_, _, _, _, _, _, acquisitionPromptClicked) =>
        _ => acquisitionPromptClicked.set(none)
      .useMemoBy((props, _, _, _, _, visitsData, acquisitionPromptClicked) =>
        (visitsData,
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
      ): (props, _, ctx, sequenceApi, _, _, _) =>
        (
          visitsData,
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
          import ctx.given

          val (visits, nextScienceIndex): (List[VisitData], StepIndex) = visitsData.value

          val acquisitionPrompt: Option[AlertRow] =
            Option.when(isWaitingAcquisitionPrompt)(
              AlertRow(
                sequenceType,
                alertPosition,
                AcquisitionPrompt(
                  sequenceApi.loadNextAtom(props.obsId, SequenceType.Science).runAsync,
                  sequenceApi.loadNextAtom(props.obsId, SequenceType.Acquisition).runAsync,
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
      .useDynTableBy: (_, resize, _, _, _, _, _, _) =>
        (DynTableDef, SizePx(resize.width.orEmpty))
      .useReactTableBy: (props, _, _, _, cols, _, _, sequence, dynTable) =>
        TableOptions(
          cols.map(dynTable.setInitialColWidths),
          sequence,
          enableColumnResizing = true,
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
          onColumnSizingChange = dynTable.onColumnSizingChangeHandler,
          meta = TableMeta(
            props.requests,
            props.executionState,
            props.progress,
            props.selectedStepId,
            props.datasetIdsInFlight
          )
        )
      .useContext(ODBQueryApi.ctx)
      // If the list of current steps changes, reload last visit and sequence.
      .useEffectWithDepsBy((props, _, _, _, _, _, _, _, _, _, _) =>
        props.currentAtomPendingSteps.map(_.id)
      ): (_, _, _, _, _, _, _, _, _, _, odbQueryApi) =>
        // TODO Maybe this should be done by ObservationSyncer. For that, we need to know there when a step
        // has completed. Maybe we can add an ODB event in the future.
        // ALSO TODO Put some state somewhere to indicate that the visits are reloading, new rows should
        // be expected soon. Otherwise, the recently completed step disappears completely for a split second.
        // During that update, numbering is inconsistent.
        _ => odbQueryApi.refreshNighttimeSequence >> odbQueryApi.refreshNighttimeVisits
      // We also refresh the visits whenever a new step starts executing. This will pull the current recorded step.
      // This is necessary so that the step doesn't disappear when it completes.
      .useEffectWithDepsBy((props, _, _, _, _, _, _, _, _, _, _) => props.currentRecordedStepId):
        (_, _, _, _, _, _, _, _, _, _, odbQueryApi) => _ => odbQueryApi.refreshNighttimeVisits
      .useRef(none[HTMLTableVirtualizer])
      .useEffectOnMountBy: (props, _, _, _, _, _, _, _, _, table, _, virtualizerRef) =>
        val autoScrollCandidates: List[String] =
          AlertRowId.toString +:
            (props.runningStepId ++ props.nextStepId).map(_.toString).toList

        // If sequence is not running, auto select next step.
        Callback.when(props.runningStepId.isEmpty)(
          props.nextStepId.map(props.setSelectedStepId).orEmpty
        ) >>
          scrollToRowId(virtualizerRef, table)(autoScrollCandidates)
            .delayMs(1) // https://github.com/TanStack/virtual/issues/615
            .toCallback
      .useEffectWithDepsBy((props, _, _, _, _, _, _, _, _, _, _, _) =>
        (props.executionState.sequenceState.isRunning,
         props.executionState.sequenceState.isWaitingNextAtom
        )
      ): (props, _, _, _, _, _, _, _, _, table, _, virtualizerRef) =>
        _ =>
          val autoScrollCandidates: List[String] =
            AlertRowId.toString +: props.runningStepId.map(_.toString).toList

          // When sequence starts or stops into a non-idle state, auto scroll to running step.
          Callback.when(props.executionState.sequenceState.isInProcess):
            scrollToRowId(virtualizerRef, table)(autoScrollCandidates)
      .render: (props, resize, _, _, cols, visits, _, _, _, table, _, virtualizerRef) =>
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
                    TagMod(
                      // Only in dev mode, show step id on hover.
                      if (LinkingInfo.developmentMode) ^.title := stepId.toString
                      else TagMod.empty,
                      (^.onClick --> props.setSelectedStepId(stepId))
                        .when:
                          step.stepTime === StepTime.Present
                        .unless:
                          props.executionState.isLocked
                    )
                  .whenDefined,
                if (step.isSelected) SequenceStyles.RowHasExtra else ObserveStyles.RowIdle,
                step match
                  case SequenceRow.Executed.ExecutedStep(_, _) => SequenceStyles.RowHasExtra
                  case _                                       => TagMod.empty,
                ObserveStyles.StepRowWithBreakpoint.when_(
                  stepIdOpt.exists(props.executionState.breakpoints.contains)
                ),
                ObserveStyles.StepRowFirstInAtom.when_(step.isFirstInAtom),
                ObserveStyles.StepRowPossibleFuture.when_(step.stepTime === StepTime.Future),
                step.stepState match
                  case s if s.hasError     => ObserveStyles.StepRowError
                  case StepState.Paused    => ObserveStyles.StepRowWarning
                  case StepState.Completed => ObserveStyles.StepRowDone
                  case StepState.Aborted   => ObserveStyles.StepRowError
                  case _                   => TagMod.empty
              )
            .orEmpty

        def computeHeaderCellMods(
          headerCell: Header[SequenceTableRowType, Any, TableMeta, Any]
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

        def computeCellMods(cell: Cell[SequenceTableRowType, Any, TableMeta, Any]): TagMod =
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

        val visitIds       = visits._1.map(_.rowId).toSet
        val collapseVisits = table.modExpanded:
          case Expanded.AllRows    => Expanded.fromCollapsedRows(visitIds.toList*)
          case Expanded.Rows(rows) => Expanded.Rows(rows ++ (visitIds.map(_ -> false)))
        val expandVisits   = table.modExpanded:
          case Expanded.AllRows    => Expanded.AllRows
          case Expanded.Rows(rows) => Expanded.Rows(rows ++ (visitIds.map(_ -> true)))

        val rows = table.getExpandedRowModel().rows

        def forAllVisits(onContains: Row[SequenceTableRowType, TableMeta] => Boolean): Boolean =
          rows.forall: row =>
            if visitIds.contains(row.id) then onContains(row) else true

        val allVisitsAreCollapsed = forAllVisits(!_.getIsExpanded())
        val allVisitsAreExpanded  = forAllVisits(_.getIsExpanded())

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

object GmosNorthSequenceTable
    extends SequenceTableBuilder[StaticConfig.GmosNorth, DynamicConfig.GmosNorth]

object GmosSouthSequenceTable
    extends SequenceTableBuilder[StaticConfig.GmosSouth, DynamicConfig.GmosSouth]
