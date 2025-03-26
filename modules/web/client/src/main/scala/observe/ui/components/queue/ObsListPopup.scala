// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.queue

import cats.Order.given
import cats.syntax.all.*
import crystal.Pot
import crystal.react.View
import crystal.react.given
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.core.model.ObservationReference
import lucuma.core.model.Program
import lucuma.core.syntax.display.*
import lucuma.react.common.*
import lucuma.react.fa.FontAwesomeIcon
import lucuma.react.primereact.*
import lucuma.react.syntax.*
import lucuma.react.table.*
import lucuma.refined.*
import lucuma.ui.LucumaIcons
import lucuma.ui.primereact.DebouncedInputText
import lucuma.ui.primereact.LucumaPrimeStyles
import lucuma.ui.reusability.given
import lucuma.ui.table.*
import observe.model.SequenceState
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.LoadedObservation
import observe.ui.model.SessionQueueRow
import observe.ui.model.reusability.given

case class ObsListPopup(
  queue:                   List[SessionQueueRow],
  obsStates:               Map[Observation.Id, SequenceState],
  loadedObs:               Option[LoadedObservation],
  loadObs:                 Reusable[Observation.Id => Callback],
  isNighttimeObsTableOpen: View[Boolean],
  linkToExploreObs:        Reusable[Either[(Program.Id, Observation.Id), ObservationReference] => VdomNode]
) extends ReactFnProps(ObsListPopup):
  val obsIdPotOpt: Option[Pot[Observation.Id]] =
    loadedObs.map(obs => obs.toPot.flatMap(_.config).as(obs.obsId))

  val isProcessing: Boolean =
    obsIdPotOpt.exists: obsIdPot =>
      obsIdPot.isPending || obsIdPot.toOption.flatMap(obsStates.get).exists(_.isRunning)

  val isNighttimeObsTableForced: Boolean =
    loadedObs.isEmpty

  val isNighttimeObsTableShown: Boolean =
    isNighttimeObsTableForced || isNighttimeObsTableOpen.get

object ObsListPopup
    extends ReactFnComponent[ObsListPopup](props =>
      val ColDef = ColumnDef[SessionQueueRow].WithColumnFilters.WithGlobalFilter[String]

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
              Icons.FileCheck
            case (Some(Pot.Ready(_)), Some(SequenceState.Running(_, _, _, _)))                 =>
              LucumaIcons.CircleNotch
            case (Some(Pot.Ready(_)), Some(SequenceState.Failed(_))) | (Some(Pot.Error(_)), _) =>
              Icons.FileCross
            case _                                                                             => EmptyVdom
        icon

      def renderCentered(node: VdomNode, css: Css = Css.Empty): VdomNode =
        <.div(ObserveStyles.Centered |+| css)(node)

      val StatusIconColumnId: ColumnId  = ColumnId("statusIcon")
      val ObsRefColumnId: ColumnId      = ColumnId("observation")
      val InstrumentColumnId: ColumnId  = ColumnId("instrument")
      val ConfigColumnId: ColumnId      = ColumnId("config")
      val TargetColumnId: ColumnId      = ColumnId("target")
      val ObsNameColumnId: ColumnId     = ColumnId("obsName")
      val ConstraintsColumnId: ColumnId = ColumnId("constraints")

      def columns(
        obsStates:        Map[Observation.Id, SequenceState],
        loadingPotOpt:    Option[Pot[Unit]],
        isProcessing:     Boolean,
        loadedObsId:      Option[Observation.Id],
        loadObs:          Observation.Id => Callback,
        linkToExploreObs: Either[(Program.Id, Observation.Id), ObservationReference] => VdomNode
      ): List[ColumnDef[SessionQueueRow, ?, Nothing, WithFilterMethod, String, ?, Nothing]] = List(
        ColDef(
          StatusIconColumnId,
          _.obsId,
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
          enableResizing = false,
          enableSorting = false
        ),
        ColDef(
          ObsRefColumnId,
          obs => obs.obsReference.toRight((obs.programId, obs.obsId)),
          header = "Obs. Id",
          cell =
            cell => <.span(cell.value.fold(_._2.shortName, _.label), linkToExploreObs(cell.value)),
          size = 240.toPx
        ).sortable.withFilterMethod(FilterMethod.Text(_.fold(_._2.shortName, _.label))),
        ColDef(
          InstrumentColumnId,
          _.instrument,
          header = "Instrument",
          cell = _.value.shortName
        ).sortable.withFilterMethod(FilterMethod.Select(_.shortName)),
        ColDef(
          ConfigColumnId,
          _.configurationSummary.getOrElse("---"),
          header = "Configuration",
          cell = _.value
        ).sortable.withFilterMethod(FilterMethod.StringText()),
        ColDef(
          TargetColumnId,
          _.title,
          header = "Target",
          cell = _.value.some.filter(_.nonEmpty).getOrElse(UnknownTargetName)
        ).sortable.withFilterMethod(FilterMethod.StringSelect()),
        ColDef(
          ConstraintsColumnId,
          _.constraintsSummary,
          header = "Constraints",
          cell = _.value
        ).sortable.withFilterMethod(FilterMethod.StringSelect()),
        ColDef(
          ObsNameColumnId,
          _.subtitle.map(_.value).getOrElse("-"),
          header = "Obs. Name",
          cell = _.value
        ).sortable.withFilterMethod(FilterMethod.StringText())
      )

      for
        cols         <-
          useMemo(
            (props.obsStates,
             props.obsIdPotOpt.map(_.void),
             props.isProcessing,
             props.loadedObs.map(_.obsId),
             props.loadObs,
             props.linkToExploreObs
            )
          )(columns(_, _, _, _, _, _))
        table        <-
          useReactTable:
            TableOptions(
              cols,
              Reusable.implicitly(props.queue),
              enableColumnResizing = true,
              columnResizeMode = ColumnResizeMode.OnChange,
              enableSorting = true,
              enableFilters = true,
              enableFacetedUniqueValues = true,
              enableGlobalFilter = true,
              globalFilterFn = FilterMethod.globalFilterFn(cols)
            )
        globalFilter <- useState("")
      yield Dialog(
        onHide = props.isNighttimeObsTableOpen.set(false),
        visible = props.isNighttimeObsTableShown,
        position = DialogPosition.Top,
        closeOnEscape = !props.isNighttimeObsTableForced,
        closable = !props.isNighttimeObsTableForced,
        modal = !props.isNighttimeObsTableForced,
        dismissableMask = !props.isNighttimeObsTableForced,
        resizable = true,
        clazz = LucumaPrimeStyles.Dialog.Large |+| ObserveStyles.Popup,
        header = <.div(ObserveStyles.ObsListHeader)(
          <.span("Candidate Observations"),
          <.span(
            DebouncedInputText(
              id = "obs-filter".refined,
              delayMillis = 250,
              value = table.getState().globalFilter.getOrElse(""),
              onChange = v => table.setGlobalFilter(v.some),
              placeholder = "<Keyword filter>"
            )
          ),
          <.span
        )
      )(
        PrimeAutoHeightVirtualizedTable(
          table,
          estimateSize = _ => 30.toPx,
          tableMod = ObserveStyles.ObserveTable |+| ObserveStyles.ObsListTable,
          columnFilterRenderer = FilterMethod.render,
          rowMod = row =>
            TagMod(
              rowClass(
                props.obsIdPotOpt.map(_.void),
                row.original,
                props.loadedObs.map(_.obsId)
              ),
              ^.onDoubleClick --> props
                .loadObs(row.original.obsId)
                .unless_(props.loadedObs.map(_.obsId).contains_(row.original.obsId))
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
