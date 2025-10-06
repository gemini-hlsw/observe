// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.obsList

import cats.Order.given
import cats.syntax.all.*
import crystal.Pot
import crystal.react.given
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.ObservationWorkflowState
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
import lucuma.ui.table.*
import observe.model.ExecutionState
import observe.model.Observer
import observe.model.SequenceState
import observe.model.UnknownTargetName
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.model.LoadedObservation
import observe.ui.model.ObsSummary
import observe.ui.model.SessionQueueRow
import observe.ui.model.enums.ObsClass
import observe.ui.model.reusability.given

case class ObsList(
  readyObservations: List[ObsSummary],
  executionState:    Map[Observation.Id, ExecutionState],
  observer:          Option[Observer],
  loadedObs:         Map[Observation.Id, LoadedObservation],
  loadObs:           Reusable[Observation.Id => Callback],
  linkToExploreObs:  Reusable[Either[(Program.Id, Observation.Id), ObservationReference] => VdomNode]
) extends ReactFnProps(ObsList):
  val rows: List[SessionQueueRow] =
    readyObservations
      .filterNot(_.workflowState === ObservationWorkflowState.Completed)
      .map: obs =>
        SessionQueueRow(
          obs,
          executionState
            .get(obs.obsId)
            .map(_.sequenceState)
            .getOrElse(SequenceState.Idle),
          observer,
          ObsClass.Nighttime,
          loadedObs.contains(obs.obsId),
          // We can't easily know step numbers nor the total number of steps.
          // Maybe we want to show pending and total times instead?
          none,
          none,
          false
        )

  val obsStates: Map[Observation.Id, SequenceState] =
    executionState.view.mapValues(_.sequenceState).toMap

  val loadedObsPots: Map[Observation.Id, Pot[Unit]] =
    loadedObs.view
      .mapValues: loadedObs =>
        loadedObs.toPot.flatMap(_.sequenceData).map(_.config).void
      .toMap

  val obsIsProcessing: Map[Observation.Id, Boolean] =
    loadedObsPots.map: (obsId, pot) =>
      obsId ->
        (pot.isPending || pot.toOption.flatMap(_ => obsStates.get(obsId)).exists(s => !s.canUnload))

object ObsList
    extends ReactFnComponent[ObsList](props =>
      val ColDef = ColumnDef[SessionQueueRow].WithColumnFilters.WithGlobalFilter[String]

      def rowClass(
        loadingPotOpt: Option[Pot[Unit]],
        row:           SessionQueueRow,
        loadedObsIds:  Set[Observation.Id]
      ): Css =
        val isLoaded: Boolean = loadedObsIds.contains_(row.obsId)

        if (row.status === SequenceState.Completed)
          ObserveStyles.RowPositive
        else if (row.status.isRunning)
          ObserveStyles.RowWarning
        else if (row.status.isError || (isLoaded && loadingPotOpt.exists(_.isError)))
          ObserveStyles.RowNegative
        else if (isLoaded && !row.status.isInProcess)
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
            case (Some(Pot.Ready(_)), Some(SequenceState.Running(_, _, _, _, _)))              =>
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
      val ConstraintsColumnId: ColumnId = ColumnId("constraints")

      def columns(
        obsStates:        Map[Observation.Id, SequenceState],
        loadedObsPots:    Map[Observation.Id, Pot[Unit]],
        obsIsProcessing:  Map[Observation.Id, Boolean],
        loadObs:          Observation.Id => Callback,
        linkToExploreObs: Either[(Program.Id, Observation.Id), ObservationReference] => VdomNode
      ): List[ColumnDef[SessionQueueRow, ?, Nothing, WithFilterMethod, String, ?, Nothing]] =
        List(
          ColDef(
            StatusIconColumnId,
            _.obsId,
            header = "",
            cell = cell =>
              renderCentered(
                if (loadedObsPots.contains(cell.value))
                  statusIconRenderer(loadedObsPots.get(cell.value), obsStates.get(cell.value))
                else
                  Button(
                    icon = Icons.FileArrowUp,
                    size = Button.Size.Small,
                    onClick = loadObs(cell.value),
                    clazz = ObserveStyles.LoadButton,
                    disabled = obsIsProcessing.get(cell.value).contains(true),
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
            cell = cell =>
              <.span(cell.value.fold(_._2.shortName, _.label), linkToExploreObs(cell.value)),
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
          ).sortable.withFilterMethod(FilterMethod.StringText()),
          ColDef(
            ConstraintsColumnId,
            _.constraintsSummary,
            header = "Constraints",
            cell = _.value
          ).sortable.withFilterMethod(FilterMethod.StringSelect())
        )

      // TODO REVISE - OR put somewhere stable.
      given [K, V: Reusability]: Reusability[Map[K, V]] = Reusability.map

      for
        cols         <-
          useMemo(
            (props.obsStates,
             props.loadedObsPots,
             props.obsIsProcessing,
             props.loadObs,
             props.linkToExploreObs
            )
          )(columns(_, _, _, _, _))
        table        <-
          useReactTable:
            TableOptions(
              cols,
              Reusable.implicitly(props.rows),
              enableColumnResizing = true,
              columnResizeMode = ColumnResizeMode.OnChange,
              enableSorting = true,
              enableFilters = true,
              enableFacetedUniqueValues = true,
              enableGlobalFilter = true,
              globalFilterFn = FilterMethod.globalFilterFn(cols)
            )
        globalFilter <- useState("")
      yield <.div(
        <.span(
          DebouncedInputText(
            id = "obs-filter".refined,
            delayMillis = 250,
            value = table.getState().globalFilter.getOrElse(""),
            onChange = v => table.setGlobalFilter(v.some),
            placeholder = "<Keyword filter>"
          )
        ),
        PrimeAutoHeightVirtualizedTable(
          table,
          estimateSize = _ => 30.toPx,
          tableMod = ObserveStyles.ObserveTable |+| ObserveStyles.ObsListTable,
          columnFilterRenderer = FilterMethod.render,
          rowMod = row =>
            TagMod(
              rowClass(
                props.loadedObsPots.get(row.original.obsId),
                row.original,
                props.loadedObsPots.keySet
              ),
              ^.onDoubleClick --> props
                .loadObs(row.original.obsId)
                .unless_(
                  props.loadedObsPots.contains(row.original.obsId) ||
                    props.obsIsProcessing(row.original.obsId)
                )
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
