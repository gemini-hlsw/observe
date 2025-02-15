// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.SizePx
import lucuma.react.common.ReactFnComponent
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

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

case class LogArea(timezone: ZoneId, globalLog: FixedLengthBuffer[LogMessage])
    extends ReactFnProps(LogArea)

object LogArea
    extends ReactFnComponent[LogArea](props =>
      val ColDef = ColumnDef[LogMessage]

      val TimeStampColId: ColumnId = ColumnId("timestamp")
      val LevelColId: ColumnId     = ColumnId("level")
      val MessageColId: ColumnId   = ColumnId("message")

      val TimeStampColWidth: SizePx = 200.toPx
      val LevelColWidth: SizePx     = 100.toPx

      for
        resizer <- useResizeDetector
        cols    <-
          useMemo(resizer.width.orEmpty): areaWidth =>
            List(
              ColDef(
                TimeStampColId,
                _.timestamp,
                size = TimeStampColWidth,
                cell = cell =>
                  val ldt: LocalDateTime =
                    ZonedDateTime.ofInstant(cell.value, props.timezone).toLocalDateTime
                  DateTimeFormatter.ISO_LOCAL_DATE.format(ldt) + " " +
                    DateTimeFormatter.ISO_LOCAL_TIME.format(ldt)
              ),
              ColDef(LevelColId, _.level, size = LevelColWidth, cell = _.value.label),
              ColDef(
                MessageColId,
                _.msg,
                cell = cell =>
                  <.span(cell.value).withTooltip(
                    content = cell.value,
                    position = Tooltip.Position.Top,
                    showDelay = 500
                  ),
                size = SizePx(areaWidth - TimeStampColWidth.value - LevelColWidth.value)
              )
            )
        rows    <-
          useMemo(props.globalLog.toChain.length): _ =>
            props.globalLog.toChain.reverse.toList
        table   <- useReactTable(TableOptions(cols, rows))
      yield PrimeAutoHeightVirtualizedTable(
        table,
        _ => 25.toPx,
        containerRef = resizer.ref,
        tableMod = ObserveStyles.LogTable,
        headerMod = ^.display.none,
        rowMod = _.original.level match
          case ObserveLogLevel.Warning => ObserveStyles.LogWarningRow
          case ObserveLogLevel.Error   => ObserveStyles.LogErrorRow
          case _                       => TagMod.empty
      )
    )
