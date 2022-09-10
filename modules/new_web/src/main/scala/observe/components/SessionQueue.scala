// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.components

import react.common.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.table.*
import observe.model.*

case class SessionQueue() extends ReactFnProps(SessionQueue.component)

object SessionQueue:
  private type Props = SessionQueue

  private val ColDef = ColumnDefiner[SessionQueueRow]()

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

  private val columns = List(
    ColDef("icon", cell = cell => <.div), //statusIconRenderer(cell.row.original)) // Compiler bug
    ColDef("addQueue", cell = cell => <.div), //addToQueueRenderer(cell.row.original)) // Compiler bug
    ColDef("class", cell = cell => <.div), //classIconRenderer(cell.row.original)) // Compiler bug
    ColDef("obsId", _.obsId, cell = _.value.toString)
    // case object StateColumn      extends TableColumn
    // case object InstrumentColumn extends TableColumn
    // case object ObsNameColumn    extends TableColumn
    // case object TargetNameColumn extends TableColumn
    // case object ObserverColumn   extends TableColumn
  )

  private val component = ScalaFnComponent.withHooks[Props].render( props => <.div)
