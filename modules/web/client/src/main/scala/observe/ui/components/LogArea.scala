// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.SizePx
import lucuma.react.common.ReactFnProps
import lucuma.react.primereact.Tooltip
import lucuma.react.primereact.tooltip.*
import lucuma.react.resizeDetector.hooks.*
import lucuma.react.syntax.*
import lucuma.react.table.*
import lucuma.ui.table.PrimeAutoHeightVirtualizedTable
import observe.common.FixedLengthBuffer
import observe.model.LogMessage
import observe.model.enums.ObserveLogLevel
import observe.ui.ObserveStyles

case class LogArea(globalLog: FixedLengthBuffer[LogMessage]) extends ReactFnProps(LogArea.component)

object LogArea:
  private type Props = LogArea

  private val ColDef = ColumnDef[LogMessage]

  private val TimeStampColId: ColumnId = ColumnId("timestamp")
  private val LevelColId: ColumnId     = ColumnId("level")
  private val MessageColId: ColumnId   = ColumnId("message")

  private val TimeStampColWidth: SizePx = 200.toPx
  private val LevelColWidth: SizePx     = 100.toPx

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useResizeDetector()
      .useMemoBy((_, resize) => resize.width.orEmpty)((_, _) =>
        areaWidth =>
          List(
            ColDef(TimeStampColId, _.timestamp.toString, size = TimeStampColWidth),
            ColDef(LevelColId, _.level.toString, size = LevelColWidth),
            ColDef(
              MessageColId,
              _.msg.value,
              cell = cell =>
                <.span(cell.value).withTooltip(
                  content = cell.value,
                  position = Tooltip.Position.Top,
                  showDelay = 500
                ),
              size = SizePx(areaWidth - TimeStampColWidth.value - LevelColWidth.value)
            )
          )
      )
      .useMemoBy((props, _, _) => props.globalLog.toChain.length)((props, _, _) =>
        _ => props.globalLog.toChain.reverse.toList
      )
      .useReactTableBy((props, _, cols, rows) => TableOptions(cols, rows))
      .render: (props, resizer, _, _, table) =>
        PrimeAutoHeightVirtualizedTable(
          table,
          _ => 25.toPx,
          containerRef = resizer.ref,
          tableMod = ObserveStyles.LogTable,
          headerMod = ^.display.none,
          rowMod = _.original.level match
            case ObserveLogLevel.WARN  => ObserveStyles.LogWarningRow
            case ObserveLogLevel.ERROR => ObserveStyles.LogErrorRow
            case _                     => TagMod.empty
        )
