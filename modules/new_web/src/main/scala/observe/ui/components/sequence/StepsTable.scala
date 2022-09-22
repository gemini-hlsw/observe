// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.core.model.sequence.Step
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
              "control",
              size = 40,
              enableResizing = false
            ),
            ColDef(
              "index",
              header = "Step",
              cell = _.row.index.toInt + 1,
              size = 60,
              enableResizing = false
            ),
            ColDef(
              "state",
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
              size = 350 // TODO this is min-width, investigate how to set it
            ),
            ColDef(
              "offsets",
              header = "Offsets",
              cell = cell => OffsetsDisplayCell(offsetsDisplay, cell.row.original),
              size = 90,
              enableResizing = false
            ),
            ColDef(
              "obsMode",
              header = "Observing Mode",
              size = 130
            ),
            ColDef(
              "exposure",
              header = "Exposure",
              cell = cell => execution.map(e => ExposureTimeCell(cell.row.original, e.instrument)),
              size = 84
            ),
            ColDef(
              "disperser",
              header = "Disperser",
              cell = cell =>
                execution.map(e => renderStringCell(cell.row.original.disperser(e.instrument))),
              size = 100
            ),
            ColDef(
              "filter",
              header = "Filter",
              cell = cell =>
                execution.map(e => renderStringCell(cell.row.original.filter(e.instrument))),
              size = 100
            ),
            ColDef(
              "fpu",
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
              size = 47
            ),
            ColDef(
              "camera",
              header = "Camera",
              size = 10
            ),
            ColDef(
              "decker",
              header = "Decker",
              size = 10
            ),
            ColDef(
              "readMode",
              header = "ReadMode",
              size = 180
            ),
            ColDef(
              "imagingMirror",
              header = "ImagingMirror",
              size = 10
            ),
            ColDef(
              "type",
              header = "Type",
              size = 75
            ),
            ColDef(
              "settings",
              size = 34,
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
          initialState = raw.mod
            .InitialTableState()
            .setColumnVisibility(
              StringDictionary(
                "obsMode"       -> false,
                "camera"        -> false,
                "decker"        -> false,
                "readMode"      -> false,
                "imagingMirror" -> false
              )
            )
        )
      )
      .render((props, _, _, table) =>

        def rowClass(index: Int, step: ExecutionStep): Css =
          step.status match
            case StepState.Running => ObserveStyles.RunningStepRow
            case _                 => Css.Empty

        PrimeVirtualizedTable(
          table,
          tableClass = ObserveStyles.ObserveTable |+| ObserveStyles.StepTable,
          rowClassFn = rowClass
        )
        // PrimeTable(table, tableClass = ObserveStyles.ObserveTable)
      )
