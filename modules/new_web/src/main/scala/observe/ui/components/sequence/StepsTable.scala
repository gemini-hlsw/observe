// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
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
import reactST.tanstackReactTable.tanstackReactTableStrings.columnVisibility
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

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useState(none[Step.Id]) // selectedStep
      .useMemoBy((props, selectedStep) =>
        (props.clientStatus, props.execution, props.offsetsDisplay, selectedStep.value)
      )((_, _) => // cols
        (clientStatus, execution, offsetsDisplay, selectedStep) =>
          List(
            ColDef(
              ControlColumnId,
              size = 40.toPx,
              enableResizing = false
            ),
            ColDef(
              IndexColumnId,
              header = "Step",
              cell = _.row.index.toInt + 1,
              size = 60.toPx,
              enableResizing = false
            ),
            ColDef(
              StateColumnId,
              header = "Execution Progress",
              cell = cell =>
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
                ),
              size = 350.toPx,
              minSize = 350.toPx
            ),
            ColDef(
              OffsetsColumnId,
              header = "Offsets",
              cell = cell => OffsetsDisplayCell(offsetsDisplay, cell.row.original),
              size = 90.toPx,
              enableResizing = false
            ),
            ColDef(
              ObsModeColumnId,
              header = "Observing Mode",
              size = 130.toPx
            ),
            ColDef(
              ExposureColumnId,
              header = "Exposure",
              cell = cell => execution.map(e => ExposureTimeCell(cell.row.original, e.instrument)),
              size = 84.toPx
            ),
            ColDef(
              DisperserColumnId,
              header = "Disperser",
              cell = cell =>
                execution.map(e => renderStringCell(cell.row.original.disperser(e.instrument))),
              size = 100.toPx
            ),
            ColDef(
              FilterColumnId,
              header = "Filter",
              cell = cell =>
                execution.map(e => renderStringCell(cell.row.original.filter(e.instrument))),
              size = 100.toPx
            ),
            ColDef(
              FPUColumnId,
              header = "FPU",
              cell = cell =>
                val step = cell.row.original
                execution.map(e =>
                  renderStringCell(
                    step
                      .fpu(e.instrument)
                      .orElse(step.fpuOrMask(e.instrument).map(_.toLowerCase.capitalize))
                  )
                )
              ,
              size = 47.toPx
            ),
            ColDef(
              CameraColumnId,
              header = "Camera",
              size = 10.toPx
            ),
            ColDef(
              DeckerColumnId,
              header = "Decker",
              size = 10.toPx
            ),
            ColDef(
              ReadModeColumnId,
              header = "ReadMode",
              size = 180.toPx
            ),
            ColDef(
              ImagingMirrorColumnId,
              header = "ImagingMirror",
              size = 10.toPx
            ),
            ColDef(
              TypeColumnId,
              header = "Type",
              size = 75.toPx
            ),
            ColDef(
              SettingsColumnId,
              size = 34.toPx,
              enableResizing = false
            )
          )
      )
      // .useMemoBy((props, _, _) => props.stepList)((_, _, _) => identity)
      .useReactTableBy((props, _, cols) =>
        TableOptions(
          cols,
          Reusable.never(props.stepList),
          enableColumnResizing = true,
          columnResizeMode = raw.mod.ColumnResizeMode.onChange, // Maybe we should use onEnd here?
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
            // case s if s.hasError                       => ObserveStyles.RowError
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
