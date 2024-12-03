// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.ReactFnProps
import lucuma.react.syntax.*
import lucuma.react.table.*
import lucuma.ui.table.PrimeAutoHeightVirtualizedTable
import observe.common.FixedLengthBuffer
import observe.model.events.LogMessage

case class LogArea(globalLog: FixedLengthBuffer[LogMessage]) extends ReactFnProps(LogArea.component)

object LogArea:
  private type Props = LogArea

  private val ColDef = ColumnDef[LogMessage]

  private val TimeStampColId = ColumnId("timestamp")
  private val LevelColId     = ColumnId("level")
  private val MessageColId   = ColumnId("message")

  private val columns =
    Reusable.always:
      List(
        ColDef(TimeStampColId, _.timestamp.toString),
        ColDef(LevelColId, _.level.toString),
        ColDef(MessageColId, _.msg.toString)
      )

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useMemoBy(props => props.globalLog.toChain.length)(props =>
        _ => props.globalLog.toChain.toList
      )
      .useReactTableBy((props, rows) => TableOptions(columns, rows))
      .render: (props, _, table) =>
        PrimeAutoHeightVirtualizedTable(
          table,
          _ => 25.toPx
        )
