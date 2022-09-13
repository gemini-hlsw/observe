// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.components

import react.common.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.table.*
import lucuma.ui.table.*
import observe.model.*
import reactST.{ tanstackTableCore => raw }
import lucuma.core.syntax.display.*
import observe.ObserveStyles
import observe.Icons
import react.fa.IconSize

case class SessionQueue(queue: List[SessionQueueRow]) extends ReactFnProps(SessionQueue.component)

object SessionQueue:
  private type Props = SessionQueue

  private val ColDef = ColumnDef[SessionQueueRow]

  private def statusIconRenderer(row: SessionQueueRow): VdomNode =
    // <.div
    val isFocused      = row.active
    // val selectedIconStyle = ObserveStyles.selectedIcon
    val icon: VdomNode =
      row.status match
        case SequenceState.Completed     => Icons.Checkmark // clazz = selectedIconStyle)
        case SequenceState.Running(_, _) => Icons.CircleNotch.copy(spin = true)
        // Icon(name = "circle notched",
        //      fitted = true,
        //      loading = true,
        //      clazz = ObserveStyles.runningIcon
        // )
        case SequenceState.Failed(_)     => EmptyVdom
        // Icon(name = "attention", color = Red, clazz = selectedIconStyle)
        // case _ if b.state.rowLoading.exists(_ === index) =>
        // Spinning icon while loading
        // IconRefresh.copy(fitted = true, loading = true, clazz = ObserveStyles.runningIcon)
        case _ if isFocused              => EmptyVdom
        // Icon(name = "dot circle outline", clazz = selectedIconStyle)
        case _                           => EmptyVdom

    // linkTo(b.props, pageOf(row))(
    //   ObserveStyles.queueIconColumn,
    icon
    // )

  private def addToQueueRenderer(row: SessionQueueRow): VdomNode =
    // val title =
    //   if (row.inDayCalQueue) "Remove from daycal queue"
    //   else "Add to daycal queue"
    // linkTo(b.props, pageOf(row))(
    //   ObserveStyles.queueIconColumn,
    //   ^.title := title,
    if (row.inDayCalQueue)
      Icons.CircleCheck.copy(size = IconSize.LG)
      //      size = Large,
      //      clazz = ObserveStyles.selectedIcon
      // )
      // ^.onClick ==> removeFromQueueE(row.obsId)
    else
      Icons.Circle.copy(size = IconSize.LG)
      //      size = Large,
      //      clazz = ObserveStyles.selectedIcon
      // )
      // ^.onClick ==> addToQueueE(row.obsId)
  // )

  private def classIconRenderer(row: SessionQueueRow): VdomNode =
    val icon: VdomNode =
      row.obsClass match
        case ObsClass.Daytime   => Icons.Sun
        case ObsClass.Nighttime => Icons.Moon
        case _                  => EmptyVdom

    // linkTo(b.props, pageOf(row))(
    //   ObserveStyles.queueIconColumn,
    icon
    // )

  private def statusText(status: SequenceState, runningStep: Option[RunningStep]): String =
    s"${status.shortName} ${runningStep.map(u => s" ${u.shortName}").getOrElse("")}"

  private def linked[T, A](
    f: raw.mod.CellContext[T, A] => VdomNode
  ): raw.mod.CellContext[T, A] => VdomNode =
    f
    //  (_, _, _, row: SessionQueueRow, _) =>
    //    linkTo(p, pageOf(row))(ObserveStyles.queueTextColumn, <.p(ObserveStyles.queueText, f(row)))

  private val columns = List(
    ColDef(
      "icon",
      cell =
        cell =>
          <.div(ObserveStyles.Centered)(statusIconRenderer(cell.row.original)), // Tooltip: Control
      size = 25,
      enableResizing = false
    ),
    ColDef(
      "addQueue",
      header = _ => <.div(ObserveStyles.Centered)(Icons.CalendarDays),          // Tooltip: Add all to queue
      cell = cell => <.div(ObserveStyles.Centered)(addToQueueRenderer(cell.row.original)),
      size = 30,
      enableResizing = false
    ),
    ColDef(
      "class",
      header = _ => <.div(ObserveStyles.Centered)(Icons.Clock),                 // Tooltip: "Obs. class"
      cell = cell => <.div(ObserveStyles.Centered)(classIconRenderer(cell.row.original)),
      size = 26,
      enableResizing = false
    ),
    ColDef("obsId", _.obsId, header = "Obs. ID", cell = linked(_.value.shortName)),
    ColDef(
      "state",
      row => (row.status, row.runningStep),
      header = "State",
      cell = linked(cell => statusText(cell.value._1, cell.value._2))
    ),
    ColDef("instrument", _.instrument, header = "Instrument", cell = linked(_.value.shortName)),
    ColDef(
      "target",
      _.targetName,
      header = "Target",
      cell = linked(_.value.getOrElse(UnknownTargetName))
    ),
    ColDef("obsName", _.name, header = "Obs. Name", cell = linked(_.value.toString))
    // ColDef("observer", _.observer.foldMap(_.value), cell = ???),
  )

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useMemo(())(_ => columns)
      .useMemoBy((_, _) => ())((props, _) => _ => props.queue)
      .useReactTableBy((_, cols, rows) =>
        TableOptions(
          cols,
          rows,
          enableColumnResizing = true,
          columnResizeMode = raw.mod.ColumnResizeMode.onChange
        )
      )
      .render((props, _, _, table) =>
        PrimeTable(table, tableClass = ObserveStyles.SessionQueueTable)
      )
