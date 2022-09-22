// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import react.common.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import observe.model.*
import lucuma.react.table.*
import reactST.{ tanstackTableCore => raw }
import lucuma.ui.table.*
import observe.ui.ObserveStyles
import lucuma.core.model.Observation
import lucuma.core.enums.Instrument
import observe.model.enums.SequenceState
import lucuma.core.model.sequence.Step
import observe.ui.model.TabOperations
import observe.ui.model.reusability.given
import lucuma.ui.reusability.given
import observe.ui.components.sequence.steps.*
import org.scalablytyped.runtime.StringDictionary
import observe.ui.model.enums.OffsetsDisplay
import observe.ui.model.extensions.*
import observe.ui.model.Execution

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
              // s => execution.map(_.instrument).flatMap(s.exposureAndCoaddsS),
              header = "Exposure",
              cell = cell => execution.map(e => ExposureTimeCell(cell.row.original, e.instrument)),
              size = 84
            ),
            ColDef(
              "disperser",
              header = "Disperser",
              size = 100
            ),
            ColDef(
              "filter",
              header = "Filter",
              size = 100
            ),
            ColDef(
              "fpu",
              header = "FPU",
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
        PrimeVirtualizedTable(table, tableClass = ObserveStyles.ObserveTable)
        // PrimeTable(table, tableClass = ObserveStyles.ObserveTable)
      )
