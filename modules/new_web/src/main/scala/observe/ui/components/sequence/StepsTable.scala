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

case class StepsTable(
  clientStatus: ClientStatus,
  execution:    Option[Execution]
//  tableState:       TableState[StepsTable.TableColumn],
//  configTableState: TableState[StepConfigTable.TableColumn]
) extends ReactFnProps(StepsTable.component):
  val stepList: List[ExecutionStep] = execution.foldMap(_.steps)

  // Find out if offsets should be displayed
  val offsetsDisplay: OffsetsDisplay = stepList.offsetsDisplay

object StepsTable:
  private type Props = StepsTable

  private val ColDef = ColumnDef[ExecutionStep]

  private def renderStringCell(value: Option[String]): VdomNode =
    <.div(ObserveStyles.ComponentLabel |+| ObserveStyles.Centered)(value.getOrElse("Unknown"))

  private val ControlColumnId: ColumnId       = ColumnId("control")
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
    ControlColumnId       -> FixedSize(40.toPx),
    IndexColumnId         -> FixedSize(60.toPx),
    StateColumnId         -> Resizable(350.toPx, min = 350.toPx.some),
    OffsetsColumnId       -> FixedSize(90.toPx),
    ObsModeColumnId       -> Resizable(130.toPx),
    ExposureColumnId      -> Resizable(84.toPx),
    DisperserColumnId     -> Resizable(120.toPx),
    FilterColumnId        -> Resizable(100.toPx),
    FPUColumnId           -> Resizable(47.toPx),
    CameraColumnId        -> Resizable(10.toPx),
    DeckerColumnId        -> Resizable(10.toPx),
    ReadModeColumnId      -> Resizable(180.toPx),
    ImagingMirrorColumnId -> Resizable(10.toPx),
    TypeColumnId          -> Resizable(75.toPx),
    SettingsColumnId      -> FixedSize(34.toPx)
  )

  private def column[V](
    id:     ColumnId,
    header: js.UndefOr[String] = js.undefined,
    cell:   js.UndefOr[raw.mod.CellContext[ExecutionStep, V] => VdomNode] = js.undefined
  ): ColumnDef[ExecutionStep, V] =
    ColDef(id, header = header, cell = cell).setColumnSize(ColumnSizes(id))

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useState(none[Step.Id]) // selectedStep
      .useMemoBy((props, selectedStep) =>
        (props.clientStatus, props.execution, props.offsetsDisplay, selectedStep.value)
      )((_, _) => // cols
        (clientStatus, execution, offsetsDisplay, selectedStep) =>
          List(
            column(ControlColumnId),
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
              cell => OffsetsDisplayCell(offsetsDisplay, cell.row.original)
            ),
            column(ObsModeColumnId, "Observing Mode"),
            column(
              ExposureColumnId,
              "Exposure",
              cell => execution.map(e => ExposureTimeCell(cell.row.original, e.instrument))
            ),
            column(
              DisperserColumnId,
              "Disperser",
              cell =>
                execution.map(e => renderStringCell(cell.row.original.disperser(e.instrument))),
            ),
            column(
              FilterColumnId,
              "Filter",
              cell => execution.map(e => renderStringCell(cell.row.original.filter(e.instrument)))
            ),
            column(
              FPUColumnId,
              "FPU",
              cell =>
                val step = cell.row.original
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
              cell => execution.map(e => ObjectTypeCell(e.instrument, cell.row.original))
            ),
            column(SettingsColumnId)
          )
      )
      // .useMemoBy((props, _, _) => props.stepList)((_, _, _) => identity)
      .useReactTableBy((props, _, cols) =>
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
          )
        )
      )
      .render((props, _, _, table) =>

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

        PrimeAutoHeightVirtualizedTable(
          table,
          // TODO Is it necessary to explicitly specify increased height of Running row?
          estimateRowHeight = _ => 40.toPx,
          tableMod = ObserveStyles.ObserveTable |+| ObserveStyles.StepTable,
          rowMod = row => rowClass(row.index.toInt, row.original),
          overscan = 5
        )
      )
