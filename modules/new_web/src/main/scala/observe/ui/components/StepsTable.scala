// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import react.common.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import observe.model.*
import lucuma.react.table.*
import reactST.{ tanstackTableCore => raw }
import lucuma.ui.table.*
import observe.ui.ObserveStyles

case class StepsTable(steps: List[ExecutionStep]) extends ReactFnProps(StepsTable.component)

object StepsTable:
  private type Props = StepsTable

  private val ColDef = ColumnDef[ExecutionStep]

  private val columns = List(
    ColDef(
      "control",
      size = 40,
      enableResizing = false
    ),
    ColDef(
      "index",
      header = "Step",
      size = 60,
      enableResizing = false
    ),
    ColDef(
      "state",
      header = "Execution Progress",
      size = 350 // TODO this is min-width, investigate how to set it
    ),
    ColDef(
      "offsets",
      header = "Offsets",
      size = 75,
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

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useMemo(())(_ => columns)
      .useMemoBy((_, _) => ())((props, _) => _ => props.steps)
      .useReactTableBy((_, cols, rows) =>
        TableOptions(
          cols,
          rows,
          enableColumnResizing = true,
          columnResizeMode = raw.mod.ColumnResizeMode.onChange
        )
      )
      .render((props, _, _, table) => PrimeTable(table))
