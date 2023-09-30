// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.queue

import cats.syntax.all.*
import crystal.react.View
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.core.syntax.display.*
import lucuma.react.common.*
import lucuma.react.fa.FontAwesomeIcon
import lucuma.react.fa.IconSize
import lucuma.react.syntax.*
import lucuma.react.table.*
import lucuma.typed.{tanstackTableCore => raw}
import lucuma.ui.reusability.given
import lucuma.ui.table.*
import observe.model.RunningStep
import observe.model.enums.SequenceState
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.SessionQueueRow
import observe.ui.model.enums.ObsClass
import observe.ui.display.given

case class SessionQueue(queue: List[SessionQueueRow], selectedObsId: View[Option[Observation.Id]])
    extends ReactFnProps(SessionQueue.component)

object SessionQueue:
  private type Props = SessionQueue

  private val ColDef = ColumnDef[SessionQueueRow]

  private def rowClass(
    /*index: Int,*/ row: SessionQueueRow,
    selectedObsId:       Option[Observation.Id]
  ): Css =
    val isFocused = selectedObsId.contains_(row.obsId)

    if (row.status === SequenceState.Completed)
      ObserveStyles.RowPositive
    else if (row.status.isRunning)
      ObserveStyles.RowWarning
    else if (row.status.isError)
      ObserveStyles.RowNegative
    else if (isFocused && !row.status.isInProcess)
      ObserveStyles.RowActive
    else
      Css.Empty

  private def statusIconRenderer(
    row:           SessionQueueRow,
    selectedObsId: Option[Observation.Id]
  ): VdomNode =
    // <.div
    val isFocused      = // row.active
      selectedObsId.contains_(row.obsId)
    // val selectedIconStyle = ObserveStyles.selectedIcon
    val icon: VdomNode =
      row.status match
        case SequenceState.Completed           => Icons.Check // clazz = selectedIconStyle)
        case SequenceState.Running(_, _, _, _) => Icons.CircleNotch.withSpin(true)
        //      loading = true,
        //      clazz = ObserveStyles.runningIcon
        case SequenceState.Failed(_)           => EmptyVdom
        // Icon(name = "attention", color = Red, clazz = selectedIconStyle)
        // case _ if b.state.rowLoading.exists(_ === index) =>
        // Spinning icon while loading
        // IconRefresh.copy(fitted = true, loading = true, clazz = ObserveStyles.runningIcon)
        case _ if isFocused                    =>             // EmptyVdom
          Icons.CircleCheck.copy(size = IconSize.LG)
        // Icon(name = "dot circle outline", clazz = selectedIconStyle)
        case _                                 => EmptyVdom

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
    s"${status.shortName} ${runningStep.map(rs => s" ${rs.shortName}").orEmpty}"

  // private def renderCell(node: VdomNode, css: Css = Css.Empty): VdomNode =
  //   // <.div(ObserveStyles.QueueText |+| css)(node)
  //   <.div(css)(node)

  private def renderCendered(node: VdomNode, css: Css = Css.Empty): VdomNode =
    <.div(ObserveStyles.Centered |+| css)(node)

  private def linked[T, A](
    f: raw.buildLibCoreCellMod.CellContext[T, A] => VdomNode
  ): raw.buildLibCoreCellMod.CellContext[T, A] => VdomNode =
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

  private def columns(selectedObsId: Option[Observation.Id]) = List(
    ColDef(
      IconColumnId,
      cell =
        cell =>
          renderCendered(statusIconRenderer(cell.row.original, selectedObsId)), // Tooltip: Control
      size = 25.toPx,
      enableResizing = false
    ),
    ColDef(
      AddQueueColumnId,
      header = _ => renderCendered(Icons.CalendarDays),                         // Tooltip: Add all to queue
      cell = cell => renderCendered(addToQueueRenderer(cell.row.original)),
      size = 30.toPx,
      enableResizing = false
    ),
    ColDef(
      ClassColumnId,
      header = _ => renderCendered(Icons.Clock),                                // Tooltip: "Obs. class"
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
      .useMemoBy(props => props.selectedObsId.get)(_ => columns(_))
      .useReactTableBy: (props, cols) =>
        TableOptions(
          cols,
          Reusable.implicitly(props.queue),
          enableColumnResizing = true,
          columnResizeMode = ColumnResizeMode.OnChange
        )
      .render: (props, _, table) =>
        <.div(ObserveStyles.SessionQueue)(
          PrimeAutoHeightVirtualizedTable(
            table,
            estimateSize = _ => 30.toPx,
            tableMod = ObserveStyles.ObserveTable |+| ObserveStyles.SessionTable,
            rowMod = row =>
              TagMod(
                rowClass( /*row.index.toInt,*/ row.original, props.selectedObsId.get),
                ^.onClick --> props.selectedObsId.set(row.original.obsId.some)
              ),
            overscan = 5
          )
        )
