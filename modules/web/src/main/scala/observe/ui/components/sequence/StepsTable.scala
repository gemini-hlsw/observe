// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.Eq
import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.*
import lucuma.core.model.sequence.gmos.*
import lucuma.react.SizePx
import lucuma.react.syntax.*
import lucuma.react.table.*
import lucuma.typed.{tanstackTableCore => raw}
import lucuma.ui.reusability.given
import lucuma.ui.table.ColumnSize.*
import lucuma.ui.table.*
import monocle.Focus
import monocle.Lens
import observe.model.SequenceStep
import observe.model.*
import observe.model.enums.SequenceState
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.sequence.steps.*
import observe.ui.model.TabOperations
import observe.ui.model.enums.OffsetsDisplay
import observe.ui.model.reusability.given
import lucuma.react.common.*
import lucuma.react.resizeDetector.hooks.*

import scala.annotation.tailrec

import scalajs.js

sealed trait StepsTable[S, D](
  protected[sequence] val instrument:    Instrument,
  protected[sequence] val nodAndShuffle: Option[GmosNodAndShuffle]
):
  def clientStatus: ClientStatus // TODO Switch to UserVault
  def obsId: Observation.Id
  def config: ExecutionConfig[S, D]
  def tabOperations: TabOperations
  def sequenceState: SequenceState
  def runningStep: Option[RunningStep]
  def nsStatus: Option[NodAndShuffleStatus]
  def isPreview: Boolean

  private def buildSequence(sequence: ExecutionSequence[D]): List[SequenceStep[D]] =
    (sequence.nextAtom +: sequence.possibleFuture)
      .flatMap(_.steps.toList)
      .map(step => SequenceStep.FutureStep(step))

  protected[sequence] lazy val steps: List[SequenceStep[D]] =
    config.acquisition.map(buildSequence).orEmpty ++ config.science.map(buildSequence).orEmpty

  // TODO: Move the next methods to some extension?
  import lucuma.core.math.Angle
  import observe.ui.utils.*
  import observe.ui.model.formatting.*

  private def maxWidth(angles: Angle*): Double =
    angles.map(angle => tableTextWidth(offsetAngle(angle))).maximumOption.orEmpty

  // Calculate the widest offset step, widest axis label and widest NS nod label
  private def sequenceOffsetMaxWidth: (Double, Double, Double) =
    nodAndShuffle.fold(
      steps
        .map(_.offset)
        .flattenOption
        .map { offset =>
          (
            maxWidth(offset.p.toAngle, offset.q.toAngle),
            math.max(tableTextWidth("p"), tableTextWidth("q")),
            0.0
          )
        }
        .foldLeft((0.0, 0.0, 0.0)) { case ((ow1, aw1, nw1), (ow2, aw2, nw2)) =>
          ((ow1.max(ow2), aw1.max(aw2), nw1.max(nw2)))
        }
    )(nsConfig =>
      (
        maxWidth(
          nsConfig.posB.p.toAngle,
          nsConfig.posB.q.toAngle,
          nsConfig.posA.p.toAngle,
          nsConfig.posA.q.toAngle
        ),
        math.max(tableTextWidth("p"), tableTextWidth("q")),
        math.max(tableTextWidth("B"), tableTextWidth("A"))
      )
    )

  protected[sequence] lazy val offsetsDisplay: OffsetsDisplay =
    (OffsetsDisplay.DisplayOffsets.apply _).tupled(sequenceOffsetMaxWidth)

case class GmosNorthStepsTable(
  clientStatus:  ClientStatus,
  obsId:         Observation.Id,
  config:        ExecutionConfig[StaticConfig.GmosNorth, DynamicConfig.GmosNorth],
  tabOperations: TabOperations,
  sequenceState: SequenceState,
  runningStep:   Option[RunningStep],
  nsStatus:      Option[NodAndShuffleStatus],
  isPreview:     Boolean
) extends ReactFnProps(GmosNorthStepsTable.component)
    with StepsTable[StaticConfig.GmosNorth, DynamicConfig.GmosNorth](
      Instrument.GmosNorth,
      config.static.nodAndShuffle
    )

case class GmosSouthStepsTable(
  clientStatus:  ClientStatus,
  obsId:         Observation.Id,
  config:        ExecutionConfig[StaticConfig.GmosSouth, DynamicConfig.GmosSouth],
  tabOperations: TabOperations,
  sequenceState: SequenceState,
  runningStep:   Option[RunningStep],
  nsStatus:      Option[NodAndShuffleStatus],
  isPreview:     Boolean
) extends ReactFnProps(GmosSouthStepsTable.component)
    with StepsTable[StaticConfig.GmosSouth, DynamicConfig.GmosSouth](
      Instrument.GmosSouth,
      config.static.nodAndShuffle
    )

// case class StepsTable(
//   clientStatus: ClientStatus,
//   obsId:        Observation.Id,
//   config:       ExecutionConfig[S, D]
// execution:    View[Option[Execution[D]]]
//  tableState:       TableState[StepsTable.TableColumn],
//  configTableState: TableState[StepConfigTable.TableColumn]
// ) extends ReactFnProps(StepsTable.component) //:
//   val stepViewList: List[View[ExecutionStep]] =
//     execution
//       .mapValue((e: View[Execution[D]]) => e.zoom(Execution.steps).toListOfViews)
//       .orEmpty

//   val steps: List[ExecutionStep] = stepViewList.map(_.get)

//   // Find out if offsets should be displayed
//   val offsetsDisplay: OffsetsDisplay = steps.offsetsDisplay

private sealed trait StepsTableBuilder[S: Eq, D: Eq]:
  private type Props = StepsTable[S, D]

  // protected val offsetDisplayCell: (
  //   OffsetsDisplay,
  //   SequenceStep[D],
  //   Option[GmosNodAndShuffle]
  // ) => VdomNode

  // private def ColDef = ColumnDef[View[StepTableRow[D]]]
  private def ColDef = ColumnDef[SequenceStep[D]]

  private def renderStringCell(value: Option[String]): VdomNode =
    <.div(ObserveStyles.ComponentLabel |+| ObserveStyles.Centered)(value.getOrElse("Unknown"))

  private val BreakpointColumnId: ColumnId    = ColumnId("breakpoint")
  // private val SkipColumnId: ColumnId          = ColumnId("skip")
  private val IconColumnId: ColumnId          = ColumnId("icon")
  private val IndexColumnId: ColumnId         = ColumnId("index")
  private val StateColumnId: ColumnId         = ColumnId("state")
  private val OffsetsColumnId: ColumnId       = ColumnId("offsets")
  private val ObsModeColumnId: ColumnId       = ColumnId("obsMode")
  private val ExposureColumnId: ColumnId      = ColumnId("exposure")
  private val GratingColumnId: ColumnId       = ColumnId("grating")
  private val FilterColumnId: ColumnId        = ColumnId("filter")
  private val FPUColumnId: ColumnId           = ColumnId("fpu")
  private val CameraColumnId: ColumnId        = ColumnId("camera")
  private val DeckerColumnId: ColumnId        = ColumnId("decker")
  private val ReadModeColumnId: ColumnId      = ColumnId("readMode")
  private val ImagingMirrorColumnId: ColumnId = ColumnId("imagingMirror")
  private val TypeColumnId: ColumnId          = ColumnId("type")
  private val SettingsColumnId: ColumnId      = ColumnId("settings")

  private val ColumnSizes: Map[ColumnId, ColumnSize] = Map(
    BreakpointColumnId    -> FixedSize(0.toPx),
    // SkipColumnId          -> FixedSize(43.toPx),
    IconColumnId          -> FixedSize(0.toPx),
    IndexColumnId         -> FixedSize(60.toPx),
    StateColumnId         -> Resizable(380.toPx, min = 380.toPx.some),
    OffsetsColumnId       -> FixedSize(90.toPx),
    ObsModeColumnId       -> Resizable(130.toPx),
    ExposureColumnId      -> Resizable(84.toPx, min = 75.toPx.some),
    GratingColumnId       -> Resizable(120.toPx, min = 120.toPx.some),
    FilterColumnId        -> Resizable(100.toPx, min = 90.toPx.some),
    FPUColumnId           -> Resizable(47.toPx, min = 75.toPx.some),
    CameraColumnId        -> Resizable(10.toPx),
    DeckerColumnId        -> Resizable(10.toPx),
    ReadModeColumnId      -> Resizable(180.toPx),
    ImagingMirrorColumnId -> Resizable(10.toPx),
    TypeColumnId          -> FixedSize(85.toPx),
    SettingsColumnId      -> FixedSize(39.toPx)
  )

  // The order in which they are removed by overflow. The ones at the beginning go first.
  // Missing columns are not removed by overflow.
  private val ColumnPriorities: List[ColumnId] = List(
    OffsetsColumnId,
    ObsModeColumnId,
    ExposureColumnId,
    GratingColumnId,
    FilterColumnId,
    FPUColumnId,
    CameraColumnId,
    DeckerColumnId,
    ReadModeColumnId,
    ImagingMirrorColumnId,
    TypeColumnId,
    SettingsColumnId
  ).reverse

  private def column[V](
    id:     ColumnId,
    header: VdomNode,
    cell:   js.UndefOr[raw.buildLibCoreCellMod.CellContext[SequenceStep[D], V] => VdomNode] =
      js.undefined
  ): ColumnDef[SequenceStep[D], V] =
    ColDef[V](id, header = _ => header, cell = cell).setColumnSize(ColumnSizes(id))

//   extension (step: View[ExecutionStep])
//     def flipBreakpoint: Callback =
//       step.zoom(ExecutionStep.breakpoint).mod(!_) >> Callback.log("TODO: Flip breakpoint")

  // START: Column computations - We could abstract this away
  case class ColState(
    resized:    ColumnSizing,
    visibility: ColumnVisibility,
    overflow:   Set[ColumnId] = Set.empty
  ):
    lazy val visible: Set[ColumnId] =
      ColumnSizes.keySet.filterNot(colId =>
        visibility.value.get(colId).contains(Visibility.Hidden)
      ) -- overflow

    lazy val visibleSizes: Map[ColumnId, SizePx] =
      visible
        .map(colId => colId -> resized.value.getOrElse(colId, ColumnSizes(colId).initial))
        .toMap

    def computedVisibility: ColumnVisibility =
      visibility.modify(_ ++ overflow.map(_ -> Visibility.Hidden))

    lazy val prioritizedRemainingCols: List[ColumnId] =
      ColumnPriorities.filter(visible.contains)

    def overflowColumn: ColState =
      ColState.overflow.modify(_ ++ prioritizedRemainingCols.headOption)(this)

    def resetOverflow: ColState =
      ColState.overflow.replace(Set.empty)(this)

  object ColState:
    val resized: Lens[ColState, ColumnSizing]        = Focus[ColState](_.resized)
    val visibility: Lens[ColState, ColumnVisibility] = Focus[ColState](_.visibility)
    val overflow: Lens[ColState, Set[ColumnId]]      = Focus[ColState](_.overflow)

  // This method:
  // - Adjusts resizable columns proportionally to available space (taking into account space taken by fixed columns).
  // - If all visible columns are at their minimum width and overflow the viewport,
  //     then starts dropping columns (as long as there are reamining droppable ones).
  private def adjustColSizes(viewportWidth: Int)(colState: ColState): ColState = {
    // Recurse at go1 when a column is dropped.
    // This level just to avoid clearing overflow on co-recursion
    @tailrec
    def go1(colState: ColState): ColState =
      if (viewportWidth === 0) colState
      else {
        // Recurse at go2 when a column was shrunk/expanded beyond its bounds.
        @tailrec
        def go2(
          remainingCols:  Map[ColumnId, SizePx],
          fixedAccum:     Map[ColumnId, SizePx] = Map.empty,
          fixedSizeAccum: Int = 0
        ): ColState = {
          val (boundedCols, unboundedCols)
            : (Iterable[(Option[ColumnId], SizePx)], Iterable[(ColumnId, SizePx)]) =
            remainingCols.partitionMap((colId, colSize) =>
              ColumnSizes(colId) match
                case FixedSize(size)                                         =>
                  (none -> size).asLeft
                // Columns that reach or go beyond their bounds are treated as fixed.
                case Resizable(_, Some(min), _) if colSize.value < min.value =>
                  (colId.some -> min).asLeft
                case Resizable(_, _, Some(max)) if colSize.value > max.value =>
                  (colId.some -> max).asLeft
                case _                                                       =>
                  (colId -> colSize).asRight
            )

          val boundedColsWidth: Int = boundedCols.map(_._2.value).sum
          val totalBounded: Int     = fixedSizeAccum + boundedColsWidth

          // If bounded columns are more than the viewport width, drop the lowest priority column and start again.
          if (totalBounded > viewportWidth && colState.prioritizedRemainingCols.nonEmpty)
            // We must remove columns one by one, since removing one causes the resto to recompute.
            go1(colState.overflowColumn)
          else
            val remainingSpace: Int = viewportWidth - totalBounded

            val totalNewUnbounded: Int = unboundedCols.map(_._2.value).sum

            val ratio: Double = remainingSpace.toDouble / totalNewUnbounded

            val newFixedAccum: Map[ColumnId, SizePx] = fixedAccum ++ boundedCols.collect {
              case (Some(colId), size) => colId -> size
            }

            val unboundedColsAdjusted: Map[ColumnId, SizePx] =
              unboundedCols
                .map((colId, width) => colId -> width.modify(x => (x * ratio).toInt))
                .toMap

            boundedCols match
              case Nil        =>
                ColState.resized.replace(ColumnSizing(newFixedAccum ++ unboundedColsAdjusted))(
                  colState
                )
              case newBounded =>
                go2(unboundedColsAdjusted, newFixedAccum, totalBounded)
        }

        go2(colState.visibleSizes)
      }

    go1(colState.resetOverflow)
  }
  // END: Column computations

  protected[sequence] val component =
    ScalaFnComponent
      .withHooks[Props]
      .useState(none[Step.Id]) // selectedStep
      .useResizeDetector()
//       // .useRef(0.0)            // tableWidth, set after table is defined
      .useMemoBy((props, selectedStep, resize) =>
        (props.clientStatus,
         props.instrument,
         //  props.config,
         props.obsId,
         props.sequenceState,
         props.runningStep,
         props.nsStatus,
         props.tabOperations,
         props.nodAndShuffle,
         props.offsetsDisplay,
         props.isPreview,
         selectedStep.value
         //  resize.width
        )
      ): (_, _, _) => // cols
        // (clientStatus, config, isPreview, nodAndShuffle, offsetsDisplay, selectedStep, _) =>
        (
          clientStatus,
          instrument,
          obsId,
          sequenceState,
          runningStep,
          nsStatus,
          tabOperations,
          nodAndShuffle,
          offsetsDisplay,
          isPreview,
          selectedStep
        ) =>
          List(
            column(
              BreakpointColumnId,
              "",
              cell =>
                val step = cell.row.original
                // val canSetBreakpoint =
                //   clientStatus.canOperate && step.get.canSetBreakpoint(
                //     execution.map(_.steps).orEmpty
                //   )

                <.div(
                  <.div(
                    ObserveStyles.BreakpointHandle
                      // ^.onClick --> step.flipBreakpoint
                  )(
                    Icons.XMark
                      .withFixedWidth()
                      .withClass(ObserveStyles.BreakpointIcon)
                      .when(step.hasBreakpoint),
                    Icons.CaretDown
                      .withFixedWidth()
                      .withClass(ObserveStyles.BreakpointIcon)
                      .unless(step.hasBreakpoint)
                  ) // .when(canSetBreakpoint)
                )
            ),
            // NO MORE SKIP THEN?
            // column(
            //   SkipColumnId,
            //   Icons.Gears,
            //   cell =>
            //     <.div(
            //       execution
            //         .map(e =>
            //           StepSkipCell(clientStatus, cell.row.original)
            //             .when(clientStatus.isLogged)
            //             .unless(e.isPreview)
            //         )
            //         .whenDefined
            //     )
            // ),
            // column(
            //   IconColumnId,
            //   "",
            //   cell =>
            //     execution.map(e =>
            //       val step = cell.row.original.get
            //       StepIconCell(step.status, step.skip, e.nextStepToRun.forall(_ === step.id))
            //     )
            // ),
            column(IndexColumnId, "Step", _.row.index.toInt + 1),
            column(
              StateColumnId,
              "Execution Progress",
              cell =>
                val step = cell.row.original
                StepProgressCell(
                  clientStatus = clientStatus,
                  instrument = instrument,
                  stepId = step.id,
                  stepType = step.stepType(nodAndShuffle.isDefined),
                  isFinished = step.isFinished,
                  stepIndex = cell.row.index.toInt,
                  obsId = obsId,
                  tabOperations = tabOperations,
                  sequenceState = sequenceState,
                  runningStep = runningStep,
                  nsStatus = nsStatus,
                  selectedStep = selectedStep,
                  isPreview = isPreview
                )
            ),
            column(
              OffsetsColumnId,
              "Offsets",
              cell =>
                cell.row.original.science.map(OffsetsDisplayCell(offsetsDisplay, _, nodAndShuffle))
            ),
            column(ObsModeColumnId, "Observing Mode"),
            column(
              ExposureColumnId,
              "Exposure",
              cell =>
                ExposureTimeCell(
                  instrument,
                  cell.row.original.exposureTime,
                  cell.row.original.stepEstimate
                )
            ),
            column(
              GratingColumnId,
              "Grating",
              cell => renderStringCell(cell.row.original.gratingName)
            ),
            column(
              FilterColumnId,
              "Filter",
              cell => renderStringCell(cell.row.original.filterName)
            ),
            column(
              FPUColumnId,
              "FPU",
              cell => renderStringCell(cell.row.original.fpuName)
            ),
            column(CameraColumnId, "Camera"),
            column(DeckerColumnId, "Decker"),
            column(ReadModeColumnId, "ReadMode"),
            column(ImagingMirrorColumnId, "ImagingMirror"),
            column(
              TypeColumnId,
              "Type",
              cell =>
                ObjectTypeCell(
                  cell.row.original.stepType(nodAndShuffle.isDefined),
                  cell.row.original.isFinished
                )
            ),
            column(
              SettingsColumnId,
              Icons.RectangleList,
              cell => SettingsCell() // TODO
            )
          )
      .useState:
        ColState(
          resized = ColumnSizing(),
          visibility = ColumnVisibility(
            ObsModeColumnId       -> Visibility.Hidden,
            CameraColumnId        -> Visibility.Hidden,
            DeckerColumnId        -> Visibility.Hidden,
            ReadModeColumnId      -> Visibility.Hidden,
            ImagingMirrorColumnId -> Visibility.Hidden
          )
        )
      .useReactTableBy: (props, _, resize, cols, colState) =>
        val viewportWidth = resize.width.filterNot(_.isEmpty).orEmpty

        TableOptions(
          cols,
          Reusable.implicitly(props.steps),
          // Reusable.never(props.stepViewList),
          enableColumnResizing = true,
          columnResizeMode = ColumnResizeMode.OnChange, // Maybe we should use OnEnd here?
          state = PartialTableState(
            columnSizing = colState.value.resized,
            columnVisibility = colState.value.computedVisibility
          ),
          onColumnSizingChange = _ match
            case Updater.Set(v)  =>
              colState.modState(s => adjustColSizes(viewportWidth)(ColState.resized.replace(v)(s)))
            case Updater.Mod(fn) =>
              colState.modState(s => adjustColSizes(viewportWidth)(ColState.resized.modify(fn)(s)))
        )
      .useEffectWithDepsBy((_, _, resize, _, _, table) => // Recompute columns upon viewport resize
        resize.width.filterNot(_.isEmpty).orEmpty
      )((_, _, _, _, colState, table) =>
        viewportWidth => colState.modState(s => adjustColSizes(viewportWidth)(s))
      )
      .render: (props, _, resize, _, _, table) =>
        def rowClass(index: Int, step: SequenceStep[D]): Css =
          step match
            // case s if s.hasError                       => ObserveStyles.StepRowError
            // case s if s.status === StepState.Running   => ObserveStyles.StepRowRunning
            // case s if s.status === StepState.Paused    => ObserveStyles.StepRowWarning
            // case s if s.status === StepState.Completed => ObserveStyles.StepRowDone
            // case s if s.status === StepState.Skipped   => ObserveStyles.RowActive
            // case s if s.status === StepState.Aborted   => ObserveStyles.RowError
            case s if s.isFinished => ObserveStyles.RowDone
            // case _                 => ObserveStyles.StepRow
            case _                 => Css.Empty

//         // val allColumnsWidth = table.getTotalSize().value
//         // val ratio           =
//         //   resize.width
//         //     .map(width => width.toDouble / allColumnsWidth)
//         //     .orEmpty

        PrimeAutoHeightVirtualizedTable(
          table,
          // TODO Is it necessary to explicitly specify increased height of Running row?
          estimateSize = _ => 40.toPx,
          containerRef = resize.ref,
          tableMod = ObserveStyles.ObserveTable |+| ObserveStyles.StepTable,
          rowMod = row =>
            rowClass(row.index.toInt, row.original) |+|
              ObserveStyles.StepRowWithBreakpoint.when_(row.original.hasBreakpoint),
          innerContainerMod = TagMod(^.width := "100%"),
          headerCellMod = { headerCell =>
            TagMod(
              headerCell.column.id match
                case id if id == BreakpointColumnId.value => ObserveStyles.BreakpointTableHeader
                // case id if id == SkipColumnId.value       => ^.colSpan := 2
                case id if id == IconColumnId.value       => ^.display.none
                case _                                    => TagMod.empty
            )
          },
          cellMod = _.column.id match
            case id if id == BreakpointColumnId.value => ObserveStyles.BreakpointTableCell
            // case id if id == SkipColumnId.value       => ObserveStyles.SkipTableCell
            case _                                    => TagMod.empty
          ,
          overscan = 5
        )

object GmosNorthStepsTable
    extends StepsTableBuilder[StaticConfig.GmosNorth, DynamicConfig.GmosNorth]

object GmosSouthStepsTable
    extends StepsTableBuilder[StaticConfig.GmosSouth, DynamicConfig.GmosSouth]
