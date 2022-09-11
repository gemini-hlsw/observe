// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.components

import react.common.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.table.*
import observe.model.*
import reactST.{tanstackTableCore => raw}
import lucuma.core.syntax.display.*

case class SessionQueue() extends ReactFnProps(SessionQueue.component)

object SessionQueue:
  private type Props = SessionQueue

  private val ColDef = ColumnDef[SessionQueueRow]

  private def statusIconRenderer(row: SessionQueueRow): VdomNode =
    <.div
    // val isFocused         = cell.row.original.active
    // val selectedIconStyle = ObserveStyles.selectedIcon
    // val icon: TagMod      =
    //   row.status match {
    //     case SequenceState.Completed                     =>
    //       Icon(name = "checkmark", clazz = selectedIconStyle)
    //     case SequenceState.Running(_, _)                 =>
    //       Icon(name = "circle notched",
    //            fitted = true,
    //            loading = true,
    //            clazz = ObserveStyles.runningIcon
    //       )
    //     case SequenceState.Failed(_)                     =>
    //       Icon(name = "attention", color = Red, clazz = selectedIconStyle)
    //     case _ if b.state.rowLoading.exists(_ === index) =>
    //       // Spinning icon while loading
    //       IconRefresh.copy(fitted = true, loading = true, clazz = ObserveStyles.runningIcon)
    //     case _ if isFocused                              =>
    //       Icon(name = "dot circle outline", clazz = selectedIconStyle)
    //     case _                                           =>
    //       <.div()
    //   }

    // linkTo(b.props, pageOf(row))(
    //   ObserveStyles.queueIconColumn,
    //   icon
    // )

  private def addToQueueRenderer(row: SessionQueueRow): VdomNode =
    <.div
    // val title =
    //   if (row.inDayCalQueue) "Remove from daycal queue"
    //   else "Add to daycal queue"
    // linkTo(b.props, pageOf(row))(
    //   ObserveStyles.queueIconColumn,
    //   ^.title := title,
    //   if (row.inDayCalQueue) {
    //     <.span(
    //       Icon(name = "check circle outline",
    //            size = Large,
    //            fitted = true,
    //            clazz = ObserveStyles.selectedIcon
    //       ),
    //       ^.onClick ==> removeFromQueueE(row.obsId)
    //     )
    //   } else {
    //     <.span(
    //       Icon(name = "circle outline",
    //            size = Large,
    //            fitted = true,
    //            clazz = ObserveStyles.selectedIcon
    //       ),
    //       ^.onClick ==> addToQueueE(row.obsId)
    //     )
    //   }
    // )

  private def classIconRenderer(row: SessionQueueRow): VdomNode =
    <.div
    // val icon: TagMod =
    //   row.obsClass match {
    //     case ObsClass.Daytime   =>
    //       IconSun.clazz(ObserveStyles.selectedIcon)
    //     case ObsClass.Nighttime =>
    //       IconMoon.clazz(ObserveStyles.selectedIcon)
    //     case _                  =>
    //       <.div()
    //   }

    // linkTo(b.props, pageOf(row))(
    //   ObserveStyles.queueIconColumn,
    //   icon
    // )

  private def linked[T, A](f: raw.mod.CellContext[T, A] => VdomNode): raw.mod.CellContext[T, A] => VdomNode =
    f
    //  (_, _, _, row: SessionQueueRow, _) =>
    //    linkTo(p, pageOf(row))(ObserveStyles.queueTextColumn, <.p(ObserveStyles.queueText, f(row)))

  private val columns = List(
    ColDef("icon", cell = cell => statusIconRenderer(cell.row.original)),
    ColDef("addQueue", cell = cell => addToQueueRenderer(cell.row.original)),
    ColDef("class", cell = cell => classIconRenderer(cell.row.original)),
    ColDef("obsId", _.obsId, cell = linked(_.value.shortName)),
    // ColDef("state", _.obsId, cell = ???)
    ColDef("instrument", _.instrument, cell = linked(_.value.shortName)),
    ColDef("targetName", _.targetName, cell = linked(_.value.getOrElse(UnknownTargetName))),
    ColDef("obsName", _.name, cell = linked(_.value.toString)),
    // ColDef("observer", _.observer.foldMap(_.value), cell = ???),
  )

  private val component = 
    ScalaFnComponent.withHooks[Props]
    .useMemo(())(_ => columns)
    .useMemo(())(_ => List.empty[SessionQueueRow])
    .useReactTableBy( (_, cols, rows) => TableOptions(cols, rows, enableColumnResizing = true)) 
    .render( (props, _, _, table) => 
      HTMLTable(table)
    )
