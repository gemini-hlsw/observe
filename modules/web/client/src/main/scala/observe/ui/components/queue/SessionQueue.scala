// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.queue

import cats.syntax.all.*
import crystal.Pot
import crystal.react.given
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.core.syntax.display.*
import lucuma.react.common.*
import lucuma.react.fa.FontAwesomeIcon
import lucuma.react.fa.IconSize
import lucuma.react.primereact.Button
import lucuma.react.syntax.*
import lucuma.react.table.*
import lucuma.ui.LucumaIcons
import lucuma.ui.reusability.given
import lucuma.ui.table.*
import observe.model.RunningStep
import observe.model.SequenceState
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.display.given
import observe.ui.model.LoadedObservation
import observe.ui.model.SessionQueueRow
import observe.ui.model.enums.ObsClass
import observe.ui.model.reusability.given

case class SessionQueue(
  queue:     List[SessionQueueRow],
  obsStates: Map[Observation.Id, SequenceState],
  selectObs: Observation.Id => Callback,
  loadedObs: Option[LoadedObservation],
  loadObs:   Observation.Id => Callback
) extends ReactFnProps(SessionQueue.component):
  val obsIdPotOpt: Option[Pot[Observation.Id]] = loadedObs.map(obs => obs.config.as(obs.obsId))

  val isProcessing: Boolean =
    obsIdPotOpt.exists: obsIdPot =>
      obsIdPot.isPending || obsIdPot.toOption.flatMap(obsStates.get).exists(_.isRunning)

object SessionQueue:
  private type Props = SessionQueue

  private val ColDef = ColumnDef[SessionQueueRow]

  private def rowClass(
    loadingPotOpt:       Option[Pot[Unit]],
    /*index: Int,*/ row: SessionQueueRow,
    selectedObsId:       Option[Observation.Id]
  ): Css =
    val isFocused = selectedObsId.contains_(row.obsId)

    if (row.status === SequenceState.Completed)
      ObserveStyles.RowPositive
    else if (row.status.isRunning)
      ObserveStyles.RowWarning
    else if (row.status.isError || (isFocused && loadingPotOpt.exists(_.isError)))
      ObserveStyles.RowNegative
    else if (isFocused && !row.status.isInProcess)
      ObserveStyles.RowActive
    else
      Css.Empty

  private def statusIconRenderer(
    loadingPotOpt: Option[Pot[Unit]],
    statusOpt:     Option[SequenceState]
  ): VdomNode =
    val icon: VdomNode =
      (loadingPotOpt, statusOpt) match
        case (Some(Pot.Pending), _)                                                        => LucumaIcons.CircleNotch
        case (Some(Pot.Ready(_)), None)                                                    => LucumaIcons.CircleNotch
        case (Some(Pot.Ready(_)), Some(SequenceState.Idle))                                => Icons.FileCheck
        case (Some(Pot.Ready(_)), Some(SequenceState.Completed))                           =>
          Icons.FileCheck // clazz = selectedIconStyle)
        case (Some(Pot.Ready(_)), Some(SequenceState.Running(_, _, _)))                    => LucumaIcons.CircleNotch
        //      clazz = ObserveStyles.runningIcon
        case (Some(Pot.Ready(_)), Some(SequenceState.Failed(_))) | (Some(Pot.Error(_)), _) =>
          Icons.FileCross
        // Icon(name = "attention", color = Red, clazz = selectedIconStyle)
        // case _ if b.state.rowLoading.exists(_ === index) =>
        // Spinning icon while loading
        // IconRefresh.copy(fitted = true, loading = true, clazz = ObserveStyles.runningIcon)
        // case _ if isFocused              =>                 // EmptyVdom
        // Icons.CircleCheck.copy(size = IconSize.LG)
        // Icon(name = "dot circle outline", clazz = selectedIconStyle)
        case _                                                                             => EmptyVdom

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

  private def renderCentered(node: VdomNode, css: Css = Css.Empty): VdomNode =
    <.div(ObserveStyles.Centered |+| css)(node)

  private def linked[T, A, TM, CM](
    f: CellContext[T, A, TM, CM] => VdomNode
  ): CellContext[T, A, TM, CM] => VdomNode =
    f // .andThen(node => renderCell(node))
    //  (_, _, _, row: SessionQueueRow, _) =>
    //    linkTo(p, pageOf(row))(ObserveStyles.queueTextColumn, <.p(ObserveStyles.queueText, f(row)))

  // private val IconColumnId: ColumnId       = ColumnId("icon")
  private val StatusIconColumnId: ColumnId = ColumnId("statusIcon")
  private val AddQueueColumnId: ColumnId   = ColumnId("addQueue")
  private val ClassColumnId: ColumnId      = ColumnId("class")
  private val ObsRefColumnId: ColumnId     = ColumnId("observation")
  private val StateColumnId: ColumnId      = ColumnId("state")
  private val InstrumentColumnId: ColumnId = ColumnId("instrument")
  private val TargetColumnId: ColumnId     = ColumnId("target")
  private val ObsNameColumnId: ColumnId    = ColumnId("obsName")
  private val ObserverColumnId: ColumnId   = ColumnId("observer")

  private def columns(
    obsStates:     Map[Observation.Id, SequenceState],
    loadingPotOpt: Option[Pot[Unit]],
    isProcessing:  Boolean,
    loadedObsId:   Option[Observation.Id],
    loadObs:       Observation.Id => Callback
  ) = List(
    ColDef(
      StatusIconColumnId,
      row => row.obsId,
      header = "",
      cell = cell =>
        renderCentered(
          if (loadedObsId.contains(cell.value))
            statusIconRenderer(loadingPotOpt, obsStates.get(cell.value))
          else
            Button(
              icon = Icons.FileArrowUp,
              size = Button.Size.Small,
              onClick = loadObs(cell.value),
              clazz = ObserveStyles.LoadButton,
              disabled = isProcessing,
              tooltip = "Load observation"
            )
        ),
      size = 25.toPx,
      enableSorting = false,
      enableResizing = false
    ),
    ColDef(
      AddQueueColumnId,
      header = _ => renderCentered(Icons.CalendarDays), // Tooltip: Add all to queue
      cell = cell => renderCentered(addToQueueRenderer(cell.row.original)),
      size = 30.toPx,
      enableResizing = false
    ),
    ColDef(
      ClassColumnId,
      header = _ => renderCentered(Icons.Clock),        // Tooltip: "Obs. class"
      cell = cell => renderCentered(classIconRenderer(cell.row.original)),
      size = 26.toPx,
      enableResizing = false
    ),
    ColDef(
      ObsRefColumnId,
      _.obsReference,
      header = "Obs. Id",
      cell = linked(_.value.map(_.label).getOrElse("---")),
      size = 110.toPx
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
      _.title,
      header = "Target",
      cell = linked(_.value.toString.some.filter(_.nonEmpty).getOrElse(UnknownTargetName))
    ),
    ColDef(
      ObsNameColumnId,
      _.subtitle.getOrElse("-"),
      header = "Obs. Name",
      cell = linked(_.value.toString)
    ),
    ColDef(
      ObserverColumnId,
      _.observer.foldMap(_.value.value),
      header = "Observer",
      cell = linked(_.value.toString)
    )
  )

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useMemoBy(props =>
        (props.obsStates,
         props.obsIdPotOpt.map(_.void),
         props.loadedObs.map(_.obsId),
         props.isProcessing
        )
      ): props =>
        (obsStates, loadingPotOpt, loadedObsId, isProcessing) =>
          columns(obsStates, loadingPotOpt, isProcessing, loadedObsId, props.loadObs)
      .useReactTableBy: (props, cols) =>
        props.queue.map(r => (r.obsId, r.obsReference)).foreach(println)
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
                rowClass(
                  props.obsIdPotOpt.map(_.void),
                  row.original,
                  props.loadedObs.map(_.obsId)
                ),
                ^.onClick --> props.selectObs(row.original.obsId)
              ),
            cellMod = cell =>
              cell.column.id match
                case StatusIconColumnId => ObserveStyles.LoadButtonCell
                case _                  => TagMod.empty
            ,
            overscan = 5
          )
        )
