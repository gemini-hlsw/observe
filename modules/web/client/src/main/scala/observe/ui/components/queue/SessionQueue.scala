// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.queue

import cats.Order.given
import cats.syntax.all.*
import crystal.Pot
import crystal.react.given
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.core.model.ObservationReference
import lucuma.core.model.Program
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
  queue:            List[SessionQueueRow],
  obsStates:        Map[Observation.Id, SequenceState],
  selectObs:        Observation.Id => Callback,
  loadedObs:        Option[LoadedObservation],
  loadObs:          Observation.Id => Callback,
  linkToExploreObs: Either[(Program.Id, Observation.Id), ObservationReference] => VdomNode
) extends ReactFnProps(SessionQueue):
  val obsIdPotOpt: Option[Pot[Observation.Id]] = loadedObs.map(obs => obs.config.as(obs.obsId))

  val isProcessing: Boolean =
    obsIdPotOpt.exists: obsIdPot =>
      obsIdPot.isPending || obsIdPot.toOption.flatMap(obsStates.get).exists(_.isRunning)

object SessionQueue
    extends ReactFnComponent[SessionQueue](props =>
      val ColDef = ColumnDef[SessionQueueRow]

      def rowClass(
        loadingPotOpt: Option[Pot[Unit]],
        row:           SessionQueueRow,
        selectedObsId: Option[Observation.Id]
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

      def statusIconRenderer(
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
            case (Some(Pot.Ready(_)), Some(SequenceState.Running(_, _, _, _)))                 =>
              LucumaIcons.CircleNotch
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

      def addToQueueRenderer(row: SessionQueueRow): VdomNode =
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

      def classIconRenderer(row: SessionQueueRow): VdomNode =
        val icon: VdomNode =
          row.obsClass match
            case ObsClass.Daytime   => Icons.Sun
            case ObsClass.Nighttime => Icons.Moon

        // linkTo(b.props, pageOf(row))(
        //   ObserveStyles.queueIconColumn,
        icon
        // )

      def statusText(status: SequenceState, runningStep: Option[RunningStep]): String =
        s"${status.shortName} ${runningStep.map(rs => s" ${rs.shortName}").orEmpty}"

      // private def renderCell(node: VdomNode, css: Css = Css.Empty): VdomNode =
      //   // <.div(ObserveStyles.QueueText |+| css)(node)
      //   <.div(css)(node)

      def renderCentered(node: VdomNode, css: Css = Css.Empty): VdomNode =
        <.div(ObserveStyles.Centered |+| css)(node)

      // private val IconColumnId: ColumnId       = ColumnId("icon")
      val StatusIconColumnId: ColumnId = ColumnId("statusIcon")
      val AddQueueColumnId: ColumnId   = ColumnId("addQueue")
      val ClassColumnId: ColumnId      = ColumnId("class")
      val ObsRefColumnId: ColumnId     = ColumnId("observation")
      val StateColumnId: ColumnId      = ColumnId("state")
      val InstrumentColumnId: ColumnId = ColumnId("instrument")
      val TargetColumnId: ColumnId     = ColumnId("target")
      val ObsNameColumnId: ColumnId    = ColumnId("obsName")
      val ObserverColumnId: ColumnId   = ColumnId("observer")

      def columns(
        obsStates:        Map[Observation.Id, SequenceState],
        loadingPotOpt:    Option[Pot[Unit]],
        isProcessing:     Boolean,
        loadedObsId:      Option[Observation.Id],
        loadObs:          Observation.Id => Callback,
        linkToExploreObs: Either[(Program.Id, Observation.Id), ObservationReference] => VdomNode
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
          obs => obs.obsReference.toRight((obs.programId, obs.obsId)),
          header = "Obs. Id",
          cell =
            cell => <.span(cell.value.fold(_._2.shortName, _.label), linkToExploreObs(cell.value)),
          size = 240.toPx
        ).sortable,
        ColDef(
          StateColumnId,
          row => statusText(row.status, row.runningStep),
          header = "State",
          cell = _.value
        ).sortable,
        ColDef(
          InstrumentColumnId,
          _.instrument,
          header = "Instrument",
          cell = _.value.shortName
        ).sortable,
        ColDef(
          TargetColumnId,
          _.title,
          header = "Target",
          cell = _.value.toString.some.filter(_.nonEmpty).getOrElse(UnknownTargetName)
        ).sortable,
        ColDef(
          ObsNameColumnId,
          _.subtitle.map(_.value).getOrElse("-"),
          header = "Obs. Name",
          cell = _.value
        ).sortable,
        ColDef(
          ObserverColumnId,
          _.observer.foldMap(_.value.value),
          header = "Observer",
          cell = _.value.toString
        ).sortable
      )

      for
        cols  <-
          useMemo(
            (props.obsStates,
             props.obsIdPotOpt.map(_.void),
             props.loadedObs.map(_.obsId),
             props.isProcessing
            )
          ): (obsStates, loadingPotOpt, loadedObsId, isProcessing) =>
            columns(
              obsStates,
              loadingPotOpt,
              isProcessing,
              loadedObsId,
              props.loadObs,
              props.linkToExploreObs
            )
        table <-
          useReactTable:
            TableOptions(
              cols,
              Reusable.implicitly(props.queue),
              enableColumnResizing = true,
              enableSorting = true,
              columnResizeMode = ColumnResizeMode.OnChange
            )
      yield <.div(ObserveStyles.SessionQueue)(
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
    )
