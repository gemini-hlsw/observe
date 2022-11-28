// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
import lucuma.react.SizePx
import lucuma.react.syntax.*
import lucuma.react.table.*
import lucuma.ui.reusability.given
import lucuma.ui.table.*
import observe.model.*
import observe.model.enums.SequenceState
import observe.model.enums.StepState
import observe.ui.ObserveStyles
import observe.ui.components.sequence.steps.*
import observe.ui.model.Execution
import observe.ui.model.TabOperations
import observe.ui.model.enums.OffsetsDisplay
import observe.ui.model.extensions.*
import observe.ui.model.reusability.given
import org.scalablytyped.runtime.StringDictionary
import react.common.*
import reactST.{ tanstackTableCore => raw }
import lucuma.ui.table.ColumnSize.*
import scalajs.js
import observe.ui.Icons
import react.resizeDetector.hooks.*
import org.scalajs.dom.HTMLDivElement
import observe.ui.components.sequence.steps.StepSkipCell
import crystal.react.View
import lucuma.ui.utils.*
import monocle.std.option.some

case class StepsTable(
  clientStatus: ClientStatus,
  execution:    View[Option[Execution]]
//  tableState:       TableState[StepsTable.TableColumn],
//  configTableState: TableState[StepConfigTable.TableColumn]
) extends ReactFnProps(StepsTable.component):
  val stepList: List[View[ExecutionStep]] =
    execution
      .mapValue((e: View[Execution]) => e.zoom(Execution.steps).toListOfViews)
      .orEmpty

  // Find out if offsets should be displayed
  val offsetsDisplay: OffsetsDisplay = stepList.map(_.get).offsetsDisplay

object StepsTable:
  private type Props = StepsTable

  private val ColDef = ColumnDef[View[ExecutionStep]]

  private def renderStringCell(value: Option[String]): VdomNode =
    <.div(ObserveStyles.ComponentLabel |+| ObserveStyles.Centered)(value.getOrElse("Unknown"))

  private val BreakpointColumnId: ColumnId    = ColumnId("breakpoint")
  // private val ControlColumnId: ColumnId       = ColumnId("control")
  private val SkipColumnId: ColumnId          = ColumnId("skip")
  private val IconColumnId: ColumnId          = ColumnId("icon")
  private val IndexColumnId: ColumnId         = ColumnId("index")
  private val StateColumnId: ColumnId         = ColumnId("state")
  private val OffsetsColumnId: ColumnId       = ColumnId("offsets")
  private val ObsModeColumnId: ColumnId       = ColumnId("obsMode")
  private val ExposureColumnId: ColumnId      = ColumnId("exposure")
  private val DisperserColumnId: ColumnId     = ColumnId("disperser")
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
    // ControlColumnId       -> FixedSize(43.toPx),
    SkipColumnId          -> FixedSize(43.toPx),
    IconColumnId          -> FixedSize(0.toPx),
    IndexColumnId         -> FixedSize(60.toPx),
    StateColumnId         -> Resizable(350.toPx, min = 350.toPx.some),
    OffsetsColumnId       -> FixedSize(90.toPx),
    ObsModeColumnId       -> Resizable(130.toPx),
    ExposureColumnId      -> Resizable(84.toPx, min = 75.toPx.some),
    DisperserColumnId     -> Resizable(120.toPx, min = 120.toPx.some),
    FilterColumnId        -> Resizable(100.toPx, min = 90.toPx.some),
    FPUColumnId           -> Resizable(47.toPx, min = 75.toPx.some),
    CameraColumnId        -> Resizable(10.toPx),
    DeckerColumnId        -> Resizable(10.toPx),
    ReadModeColumnId      -> Resizable(180.toPx),
    ImagingMirrorColumnId -> Resizable(10.toPx),
    TypeColumnId          -> FixedSize(85.toPx),
    SettingsColumnId      -> FixedSize(37.toPx)
  )

  // private val ColumnSizes: Map[ColumnId, ColumnSize] = ColumnSizesBase +
  //   (StateColumnId -> Resizable(
  //     // (__WIDTH - ColumnSizesBase.values.foldLeft(0)((w, colSize) => w + colSize.size.value)).toPx,
  //     __WIDTH.toPx,
  //     min_ = (__WIDTH - ColumnSizesBase.values.foldLeft(0)((w, colSize) =>
  //       w + colSize.size.value
  //     )).toPx.some
  //     // min = 350.toPx.some
  //   ))

  private def column[V](
    id:     ColumnId,
    header: VdomNode,
    cell:   js.UndefOr[raw.mod.CellContext[View[ExecutionStep], V] => VdomNode] = js.undefined
  ): ColumnDef[View[ExecutionStep], V] =
    ColDef[V](id, header = _ => header, cell = cell).setColumnSize(ColumnSizes(id))

  extension (step: View[ExecutionStep])
    def flipBreakpoint: Callback =
      step.zoom(ExecutionStep.breakpoint).mod(!_)

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useState(none[Step.Id])  // selectedStep
      .useResizeDetector()
      .useState(0.0)            // ratio, set after table is defined
      .useMemoBy((props, selectedStep, resize, _) =>
        (props.clientStatus,
         props.execution.get,
         props.offsetsDisplay,
         selectedStep.value,
         resize.width
        )
      )((_, _, _, _) => // cols
        (clientStatus, execution, offsetsDisplay, selectedStep, width) =>
          List(
            column(
              BreakpointColumnId,
              "",
              cell =>
                val step = cell.row.original

                <.div(
                  ObserveStyles.BreakpointHandle,
                  ^.onClick --> step.flipBreakpoint
                )(
                  Icons.XMark
                    .withFixedWidth()
                    .withClass(ObserveStyles.BreakpointIcon)
                    //     ^.onMouseEnter --> props.breakPointEnterCB(p.step.id),
                    //     ^.onMouseLeave --> props.breakPointLeaveCB(p.step.id)
                    // )
                    .when(step.get.breakpoint),
                  Icons.CaretDown
                    .withFixedWidth()
                    .withClass(ObserveStyles.BreakpointIcon)
                    //     ^.onMouseEnter --> props.breakPointEnterCB(p.step.id),
                    //     ^.onMouseLeave --> props.breakPointLeaveCB(p.step.id)
                    // )
                    .unless(step.get.breakpoint)
                ) // .when(canSetBreakpoint),
            ),
            column(
              SkipColumnId,
              Icons.Gears,
              cell =>
                execution.map(e =>
                  StepSkipCell(
                    clientStatus,
                    cell.row.original,
                    e.obsId,
                    e.obsName
                    // false, // canSetBreakpoint(row.step, f.steps),
                    // null,  // rowBreakpointHoverOnCB,
                    // null,  // rowBreakpointHoverOffCB,
                    // null   // recomputeHeightsCB
                  )
                )
              // ).when(clientStatus.isLogged)
              //   .unless(e.isPreview)

              // StepToolsCell(
              //   clientStatus,
              //   cell.row.original,
              //   30,    // rowHeight($)(cell.row.original.id),
              //   30,    // $.props.rowDetailsHeight(row.step, $.state.selected),
              //   e.isPreview,
              //   e.nextStepToRun,
              //   e.obsId,
              //   e.obsName,
              //   false, // canSetBreakpoint(row.step, f.steps),
              //   null,  // rowBreakpointHoverOnCB,
              //   null,  // rowBreakpointHoverOffCB,
              //   null   // recomputeHeightsCB
              // )
              // )
            ),
            column(
              IconColumnId,
              "",
              cell =>
                execution.map(e =>
                  val step = cell.row.original.get

                  StepIconCell(
                    step.status,
                    step.skip,
                    e.nextStepToRun.forall(_ === step.id),
                    0 // props.rowHeight - props.secondRowHeight
                  )
                )
            ),
            column(IndexColumnId, "Step", _.row.index.toInt + 1),
            column(
              StateColumnId,
              "Execution Progress",
              cell =>
                execution.map(e =>
                  StepProgressCell(
                    clientStatus = clientStatus,
                    step = cell.row.original,
                    stepIndex = cell.row.index.toInt,
                    obsId = e.obsId,
                    instrument = e.instrument,
                    tabOperations = e.tabOperations,
                    sequenceState = e.sequenceState,
                    selectedStep = selectedStep,
                    isPreview = e.isPreview
                  )
                )
            ),
            column(
              OffsetsColumnId,
              "Offsets",
              cell => OffsetsDisplayCell(offsetsDisplay, cell.row.original.get)
            ),
            column(ObsModeColumnId, "Observing Mode"),
            column(
              ExposureColumnId,
              "Exposure",
              cell => execution.map(e => ExposureTimeCell(cell.row.original.get, e.instrument))
            ),
            column(
              DisperserColumnId,
              "Disperser",
              cell =>
                execution.map(e => renderStringCell(cell.row.original.get.disperser(e.instrument))),
            ),
            column(
              FilterColumnId,
              "Filter",
              cell =>
                execution.map(e => renderStringCell(cell.row.original.get.filter(e.instrument)))
            ),
            column(
              FPUColumnId,
              "FPU",
              cell =>
                val step = cell.row.original.get

                execution.map(e =>
                  renderStringCell(
                    step
                      .fpu(e.instrument)
                      .orElse(step.fpuOrMask(e.instrument).map(_.toLowerCase.capitalize))
                  )
                )
            ),
            column(CameraColumnId, "Camera"),
            column(DeckerColumnId, "Decker"),
            column(ReadModeColumnId, "ReadMode"),
            column(ImagingMirrorColumnId, "ImagingMirror"),
            column(
              TypeColumnId,
              "Type",
              cell => execution.map(e => ObjectTypeCell(e.instrument, cell.row.original.get))
            ),
            column(
              SettingsColumnId,
              Icons.RectangleList,
              cell =>
                execution.map(e =>
                  SettingsCell(e.instrument, e.obsId, cell.row.original.get.id, e.isPreview)
                )
            )
          )
      )
      // .useMemoBy((props, _, _) => props.stepList)((_, _, _) => identity)
      .useState(ColumnSizing()) // colSizes
      .useReactTableBy((props, _, resize, _, cols, colSizes) =>

        println(colSizes.value)

        TableOptions(
          cols,
          Reusable.never(props.stepList),
          enableColumnResizing = true,
          columnResizeMode = ColumnResizeMode.OnChange, // Maybe we should use OnEnd here?
          initialState = TableState(
            columnVisibility = ColumnVisibility(
              ObsModeColumnId       -> Visibility.Hidden,
              CameraColumnId        -> Visibility.Hidden,
              DeckerColumnId        -> Visibility.Hidden,
              ReadModeColumnId      -> Visibility.Hidden,
              ImagingMirrorColumnId -> Visibility.Hidden
            )
          ),
          state = PartialTableState(columnSizing = colSizes.value),
          onColumnSizingChange = _ match
            case Updater.Set(v)  => colSizes.setState(v)
            case Updater.Mod(fn) => colSizes.modState(fn)
        )
      )
      // .useState(true)          // initialRender
      // .useEffectOnMountBy((_, _, _, _, initialRender) => initialRender.setState(false))
      .useEffectWithDepsBy((_, _, resize, _, _, _, _) => resize.width.filterNot(_.isEmpty))(
        // .useEffectBy(
        (_, _, resize, _, _, _, table) =>
          //   resize.width
          _.map(width =>
            val fixedColsWidth =
              table
                .getVisibleFlatColumns()
                .map(col => ColumnSizes(col.id))
                .collect { case FixedSize(size) => size.value }
                .sum

            val currentResizableColumnsWidth = table.getTotalSize().value - fixedColsWidth
            val ratio                        = (width - fixedColsWidth).toDouble / currentResizableColumnsWidth

            Callback.log(s"RESIZING! Ratio: $ratio") >>
              table.modColumnSizing(
                _.modify(resizedCols =>
                  // val initialResizableSizes: Map[ColumnId, SizePx] =

                  println(s"BEFORE: $resizedCols")

                  val newCols =
                    table
                      .getVisibleFlatColumns()
                      .map(col => (col.id, ColumnSizes(col.id)))
                      .collect { case (colId, Resizable(initial, _, _)) =>
                        colId -> resizedCols
                          .getOrElse(colId, initial)
                          .modify(s => (s * ratio).toInt)
                      }
                      .toMap

                  // println(cols)
                  // val newCols = cols.map((colId, size) =>
                  //   colId ->
                  //     // (ColumnSizes.get(colId) match
                  //     //   case Some(Resizable(_, _, _)) => size.modify(s => (s * ratio).toInt)
                  //     //   case _                        => size
                  //     // )
                  // )

                  println(s"AFTER: $newCols")

                  newCols
                )
              )
          )
            .getOrElse(Callback.log(resize))
        // .orEmpty

        // table
        //   .getAllColumns()
        //   .filter(_.getIsVisible())
        //   .map(col => (col, ColumnSizes(col.id))) // columnSizes of id + col)
        //   .collect { case (col, Resizable(_, _, _)) =>
        //     table.modColumnSizing(colSizing => colSizing)
        //   // Callback.empty // col.setnewidth
        //   }
        //   .sequence
        //   .void
      )
      .useEffectWithDepsBy((_, _, resize, _, _, _, table) =>
        (resize.width.filterNot(_.isEmpty).orEmpty, table.getTotalSize().value)
      )((_, _, _, ratio, _, _, table) =>
        (viewportWidth, tableWidth) =>
          val fixedColsWidth =
            table
              .getVisibleFlatColumns()
              .map(col => ColumnSizes(col.id))
              .collect { case FixedSize(size) => size.value }
              .sum

          val currentResizableColumnsWidth = tableWidth - fixedColsWidth
          // val ratio                        =

          Callback.log(viewportWidth.toDouble / tableWidth) >> ratio.setState(
            // viewportWidth.toDouble / tableWidth
            (viewportWidth - fixedColsWidth).toDouble / currentResizableColumnsWidth
          )
      )
      .render((props, _, resize, _, _, colSizes, table) => // , initialRender) =>

        def rowClass(index: Int, step: ExecutionStep): Css =
          step match
            case s if s.hasError                       => ObserveStyles.StepRowError
            case s if s.status === StepState.Running   => ObserveStyles.StepRowRunning
            case s if s.status === StepState.Paused    => ObserveStyles.StepRowWarning
            case s if s.status === StepState.Completed => ObserveStyles.StepRowDone
            // case s if s.status === StepState.Skipped   => ObserveStyles.RowActive
            // case s if s.status === StepState.Aborted   => ObserveStyles.RowError
            // case s if s.isFinished                     => ObserveStyles.RowDone
            // case _                                     => ObserveStyles.StepRow
            case _                                     => Css.Empty

        org.scalajs.dom.console.log(
          table
            .getHeaderGroups()
            .map(headerGroup => headerGroup.headers.map(header => header.getSize()))
        )
        // println(colSizes.value)

        val allColumnsWidth = table.getTotalSize().value
        val ratio           =
          resize.width
            .map(width => width.toDouble / allColumnsWidth)
            .orEmpty

        // <.div.withRef(resize.ref)(^.width := "100%", ^.height := "100%")(
        // resize.width.map(width =>
        PrimeAutoHeightVirtualizedTable(
          // PrimeVirtualizedTable(
          table,
          // TODO Is it necessary to explicitly specify increased height of Running row?
          estimateSize = _ => 40.toPx,
          containerRef = resize.ref.asInstanceOf[Ref.Simple[HTMLDivElement]],
          tableMod = ObserveStyles.ObserveTable |+| ObserveStyles.StepTable,
          rowMod = row =>
            rowClass(row.index.toInt, row.original.get) |+|
              ObserveStyles.StepRowWithBreakpoint.when_(row.original.get.breakpoint),
          innerContainerMod = TagMod(^.width := "100%"),
          // containerMod = ^.height := "300px",
          // headerCellMod = { (headerCell: raw.mod.Header[View[ExecutionStep], Any]) =>
          headerCellMod = { headerCell =>
            TagMod(
              ^.width := (headerCell.id match
                  // case colId if ColumnId(colId) == StateColumnId =>
                  //   val minStateColWidth =
                  //     __WIDTH - headerCell
                  //       .getContext()
                  //       .table
                  //       .getAllLeafColumns()
                  //       .filterNot(_.id == StateColumnId.value)
                  //       .filter(_.getIsVisible())
                  //       .foldLeft(0.0)((w, col) => w + col.getSize())

                  //   println(minStateColWidth)
                  //   println(headerCell.getSize())

                  //   s"${math.max(minStateColWidth, headerCell.getSize())}px"
                  case colId =>
                    ColumnSizes.get(ColumnId(colId)) match
                      //     case _ => s"${headerCell.getSize()}px"
                      case Some(FixedSize(width))   => s"${width}px"
                      // case Some(Resizable(_, _, _)) => s"${headerCell.getSize() * 100 / __WIDTH}%"
                      case Some(Resizable(_, _, _)) => s"${headerCell.getSize() * ratio}%"
                      // multiply minSize and maxSize by ratio too!!!!
                      case _                        => "0"
                // case _ => s"${headerCell.getSize() * 100 / __WIDTH}%"
              ),
              // TagMod.when(ColumnId(headerCell.id) == StateColumnId)(
              //   ^.minWidth := s"${(__WIDTH - headerCell
              //       .getContext()
              //       .table
              //       .getAllLeafColumns()
              //       .filterNot(_.id == StateColumnId.value)
              //       .filter(_.getIsVisible())
              //       .foldLeft(0.0)((w, col) => w + col.getSize()))}px"
              // )
              //   )
              // },
              // headerCellMod = _.column.id match
              headerCell.column.id match
                case id if id == BreakpointColumnId.value => ObserveStyles.BreakpointTableHeader
                case id if id == SkipColumnId.value       => ^.colSpan := 2
                case id if id == IconColumnId.value       =>            ^.display.none
                case _                                    => TagMod.empty
            )
          },
          cellMod = _.column.id match
            case id if id == BreakpointColumnId.value => ObserveStyles.BreakpointTableCell
            case id if id == SkipColumnId.value       => ObserveStyles.SkipTableCell
            // case id if id == ControlColumnId.value    => ObserveStyles.ControlTableCell
            case _                                    => TagMod.empty
          ,
          overscan = 5
        )
      )
    // )
    // )
