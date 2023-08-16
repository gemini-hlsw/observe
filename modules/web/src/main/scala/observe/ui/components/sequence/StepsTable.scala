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
import lucuma.react.common.*
import lucuma.react.resizeDetector.hooks.*
import lucuma.react.syntax.*
import lucuma.react.table.*
import lucuma.typed.{tanstackTableCore => raw}
import lucuma.ui.reusability.given
import lucuma.ui.sequence.SequenceRow
import lucuma.ui.sequence.SequenceRowFormatters.*
import lucuma.ui.table.ColumnSize.*
import lucuma.ui.table.*
import lucuma.ui.table.hooks.*
import observe.model.*
import observe.model.enums.SequenceState
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.sequence.steps.*
import observe.ui.model.TabOperations
import observe.ui.model.enums.OffsetsDisplay
import observe.ui.model.reusability.given

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

  private def buildSequence(sequence: ExecutionSequence[D]): List[SequenceRow.FutureStep[D]] =
    SequenceRow.FutureStep.fromAtoms(sequence.nextAtom +: sequence.possibleFuture)

  protected[sequence] lazy val steps: List[SequenceRow.FutureStep[D]] =
    config.acquisition.map(buildSequence).orEmpty ++ config.science.map(buildSequence).orEmpty

  // TODO: Move the next methods to some extension?
  import observe.ui.utils.*

  private def maxWidth(strings: String*): Double =
    strings.map(tableTextWidth).maximumOption.orEmpty

  // Calculate the widest offset step, widest axis label and widest NS nod label
  private def sequenceOffsetMaxWidth: (Double, Double, Double) =
    nodAndShuffle.fold(
      steps
        .map(_.offset)
        .flattenOption
        .map { offset =>
          (
            maxWidth(FormatOffsetP(offset.p).value, FormatOffsetQ(offset.q).value),
            maxWidth("p", "q"),
            0.0
          )
        }
        .foldLeft((0.0, 0.0, 0.0)) { case ((ow1, aw1, nw1), (ow2, aw2, nw2)) =>
          ((ow1.max(ow2), aw1.max(aw2), nw1.max(nw2)))
        }
    )(nsConfig =>
      (
        maxWidth(
          FormatOffsetP(nsConfig.posB.p).value,
          FormatOffsetQ(nsConfig.posB.q).value,
          FormatOffsetP(nsConfig.posA.p).value,
          FormatOffsetQ(nsConfig.posA.q).value
        ),
        maxWidth("p", "q"),
        maxWidth("B", "A")
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

private sealed trait StepsTableBuilder[S: Eq, D: Eq]:
  private type Props = StepsTable[S, D]

  private def ColDef = ColumnDef[SequenceRow.FutureStep[D]]

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

  val DynTableDef = DynTable(
    ColumnSizes,
    ColumnPriorities,
    DynTable.ColState(
      resized = ColumnSizing(),
      visibility = ColumnVisibility(
        ObsModeColumnId       -> Visibility.Hidden,
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
      raw.buildLibCoreCellMod.CellContext[SequenceRow.FutureStep[D], V] => VdomNode
    ] = js.undefined
  ): ColumnDef[SequenceRow.FutureStep[D], V] =
    ColDef[V](id, header = _ => header, cell = cell).setColumnSize(ColumnSizes(id))

//   extension (step: View[ExecutionStep])
//     def flipBreakpoint: Callback =
//       step.zoom(ExecutionStep.breakpoint).mod(!_) >> Callback.log("TODO: Flip breakpoint")

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
                step
                  .stepType(nodAndShuffle.isDefined)
                  .map: stepType =>
                    StepProgressCell(
                      clientStatus = clientStatus,
                      instrument = instrument,
                      stepId = step.stepId,
                      stepType = stepType,
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
                cell.row.original.offset.map: offset =>
                  OffsetsDisplayCell(
                    offsetsDisplay,
                    offset,
                    cell.row.original.hasGuiding,
                    nodAndShuffle
                  )
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
                cell.row.original
                  .stepType(nodAndShuffle.isDefined)
                  .map: stepType =>
                    ObjectTypeCell(
                      stepType,
                      cell.row.original.isFinished
                    )
            ),
            column(
              SettingsColumnId,
              Icons.RectangleList,
              cell => SettingsCell() // TODO
            )
          )
      .useDynTableBy((_, _, resize, _) => (DynTableDef, SizePx(resize.width.orEmpty)))
      .useReactTableBy: (props, _, resize, cols, dynTable) =>
        TableOptions(
          cols,
          Reusable.implicitly(props.steps),
          enableColumnResizing = true,
          columnResizeMode = ColumnResizeMode.OnChange, // Maybe we should use OnEnd here?
          state = PartialTableState(
            columnSizing = dynTable.columnSizing,
            columnVisibility = dynTable.columnVisibility
          ),
          onColumnSizingChange = dynTable.onColumnSizingChangeHandler
        )
      .render: (props, _, resize, _, _, table) =>
        def rowClass(index: Int, step: SequenceRow.FutureStep[D]): Css =
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
