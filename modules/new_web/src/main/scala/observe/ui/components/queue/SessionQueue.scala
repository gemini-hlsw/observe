// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.queue

import cats.syntax.all.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.syntax.display.*
import lucuma.react.syntax.*
import lucuma.react.table.*
import lucuma.ui.table.*
import observe.model.RunningStep
import observe.model.enums.SequenceState
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.SessionQueueFilter
import observe.ui.model.SessionQueueRow
import observe.ui.model.enums.ObsClass
import observe.ui.model.reusability.given
import react.common.*
import react.fa.FontAwesomeIcon
import react.fa.IconSize
import react.primereact.*
// import reactST.primereact.components.*
// import reactST.primereact.selectitemMod.SelectItem
import reactST.{ tanstackTableCore => raw }

import scalajs.js.JSConverters.*

case class SessionQueue(queue: List[SessionQueueRow]) extends ReactFnProps(SessionQueue.component)

object SessionQueue:
  private type Props = SessionQueue

  private val ColDef = ColumnDef[SessionQueueRow]

  private def rowClass(index: Int, row: SessionQueueRow): Css =
    if (row.status === SequenceState.Completed)
      ObserveStyles.RowPositive
    else if (row.status.isRunning)
      ObserveStyles.RowWarning
    else if (row.status.isError)
      ObserveStyles.RowNegative
    else if (row.active && !row.status.isInProcess)
      ObserveStyles.RowActive
    else
      Css.Empty

  private def statusIconRenderer(row: SessionQueueRow): VdomNode =
    // <.div
    val isFocused      = row.active
    // val selectedIconStyle = ObserveStyles.selectedIcon
    val icon: VdomNode =
      row.status match
        case SequenceState.Completed     => Icons.Check // clazz = selectedIconStyle)
        case SequenceState.Running(_, _) => Icons.CircleNotch.withSpin(true)
        //      loading = true,
        //      clazz = ObserveStyles.runningIcon
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

    // linkTo(b.props, pageOf(row))(
    //   ObserveStyles.queueIconColumn,
    icon
    // )

  private def statusText(status: SequenceState, runningStep: Option[RunningStep]): String =
    s"${status.shortName} ${runningStep.map(u => s" ${u.shortName}").orEmpty}"

  // private def renderCell(node: VdomNode, css: Css = Css.Empty): VdomNode =
  //   // <.div(ObserveStyles.QueueText |+| css)(node)
  //   <.div(css)(node)

  private def renderCendered(node: VdomNode, css: Css = Css.Empty): VdomNode =
    <.div(ObserveStyles.Centered |+| css)(node)

  private def linked[T, A](
    f: raw.mod.CellContext[T, A] => VdomNode
  ): raw.mod.CellContext[T, A] => VdomNode =
    f // .andThen(node => renderCell(node))
    //  (_, _, _, row: SessionQueueRow, _) =>
    //    linkTo(p, pageOf(row))(ObserveStyles.queueTextColumn, <.p(ObserveStyles.queueText, f(row)))

  private val IconColumnId: ColumnId       = ColumnId("icon")
  private val AddQueueColumnId: ColumnId   = ColumnId("addQueue")
  private val ClassColumnId: ColumnId      = ColumnId("class")
  private val ObsIdColumnId: ColumnId      = ColumnId("obsId")
  private val StateColumnId: ColumnId      = ColumnId("state")
  private val InstrumentColumnId: ColumnId = ColumnId("instrument")
  private val TargetColumnId: ColumnId     = ColumnId("target")
  private val ObsNameColumnId: ColumnId    = ColumnId("obsName")
  private val ObserverColumnId: ColumnId   = ColumnId("observer")

  private val columns = List(
    ColDef(
      IconColumnId,
      cell = cell => renderCendered(statusIconRenderer(cell.row.original)), // Tooltip: Control
      size = 25.toPx,
      enableResizing = false
    ),
    ColDef(
      AddQueueColumnId,
      header = _ => renderCendered(Icons.CalendarDays),                     // Tooltip: Add all to queue
      cell = cell => renderCendered(addToQueueRenderer(cell.row.original)),
      size = 30.toPx,
      enableResizing = false
    ),
    ColDef(
      ClassColumnId,
      header = _ => renderCendered(Icons.Clock),                            // Tooltip: "Obs. class"
      cell = cell => renderCendered(classIconRenderer(cell.row.original)),
      size = 26.toPx,
      enableResizing = false
    ),
    ColDef(
      ObsIdColumnId,
      _.obsId,
      header = "Obs. ID",
      cell = linked(_.value.shortName),
      size = 100.toPx
    ),
    ColDef(
      StateColumnId,
      row => (row.status, row.runningStep),
      header = "State",
      cell = linked(cell => statusText(cell.value._1, cell.value._2))
    ),
    ColDef(
      InstrumentColumnId,
      _.instrument,
      header = "Instrument",
      cell = linked(_.value.shortName)
    ),
    ColDef(
      TargetColumnId,
      _.targetName,
      header = "Target",
      cell = linked(_.value.getOrElse(UnknownTargetName))
    ),
    ColDef(
      ObsNameColumnId,
      _.name,
      header = "Obs. Name",
      cell = linked(_.value.toString)
    ),
    ColDef(
      ObserverColumnId,
      _.observer.foldMap(_.value),
      header = "Observer",
      cell = linked(_.value.toString)
    )
  )

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useState(SessionQueueFilter.All)
      .useMemo(())(_ => columns)
      .useMemoBy((_, filter, _) => filter)((props, _, _) =>
        filter => filter.value.filter(props.queue)
      )
      .useReactTableBy((_, _, cols, rows) =>
        TableOptions(
          cols,
          rows,
          enableColumnResizing = true,
          columnResizeMode = ColumnResizeMode.OnChange
        )
      )
      .render((props, filter, _, _, table) =>
        <.div(ObserveStyles.SessionQueue)(
          PrimeAutoHeightVirtualizedTable(
            table,
            estimateSize = _ => 30.toPx,
            tableMod = ObserveStyles.ObserveTable,
            rowMod = row => rowClass(row.index.toInt, row.original),
            overscan = 5
          ),
          SelectButtonOptional(
            clazz = ObserveStyles.ObsClassSelect,
            value = filter.value.value,
            options = ObsClass.values.toList.map(v => SelectItem(v)),
            itemTemplate = _.value match
              case ObsClass.Daytime   => React.Fragment(Icons.Sun, "Daytime").rawElement
              case ObsClass.Nighttime => React.Fragment(Icons.Moon, "Nighttime").rawElement
            ,
            onChange = value => filter.setState(SessionQueueFilter(value))
          )
        )
      )
