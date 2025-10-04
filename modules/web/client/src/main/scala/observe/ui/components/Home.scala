// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.*
import crystal.Pot
import crystal.react.*
import crystal.syntax.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.ObservationWorkflowState
import lucuma.core.model.Observation
import lucuma.core.model.ObservationReference
import lucuma.core.model.Program
import lucuma.react.common.ReactFnComponent
import lucuma.react.common.ReactFnProps
import lucuma.react.primereact.*
import lucuma.react.primereact.tooltip.*
import lucuma.ui.syntax.all.*
import observe.model.ClientConfig
import observe.model.SequenceState
import observe.ui.Icons
import observe.ui.ObserveStyles
import observe.ui.components.queue.ObsListPopup
import observe.ui.components.sequence.ObservationExecutionDisplay
import observe.ui.model.AppContext
import observe.ui.model.LoadedObservation
import observe.ui.model.RootModel
import observe.ui.model.RootModelData
import observe.ui.model.SessionQueueRow
import observe.ui.model.enums.ObsClass
import observe.ui.services.SequenceApi

case class Home(rootModel: RootModel) extends ReactFnProps(Home)

object Home
    extends ReactFnComponent[Home](props =>
      for
        ctx         <- useContext(AppContext.ctx)
        sequenceApi <- useContext(SequenceApi.ctx)
      yield
        import ctx.given

        val clientConfigPot: Pot[ClientConfig] = props.rootModel.clientConfig
        val rootModelData: RootModelData       = props.rootModel.data.get

        val loadedObs: Option[LoadedObservation] = rootModelData.nighttimeObservation

        val isObsTableOpen: View[Boolean] =
          props.rootModel.data.zoom(RootModelData.isObsTableOpen)

        val openObsTable: Callback =
          isObsTableOpen.set(true)

        val closeObsTable: Callback =
          isObsTableOpen.set(false)

        val loadObservation: Reusable[Observation.Id => Callback] =
          Reusable
            .implicitly(rootModelData.readyObservationsMap.keySet)
            .withValue: obsId =>
              rootModelData.readyObservationsMap
                .get(obsId)
                .foldMap: obsRow =>
                  props.rootModel.data
                    .zoom(RootModelData.nighttimeObservation)
                    .set(LoadedObservation(obsId).some) >>
                    sequenceApi.loadObservation(obsId, obsRow.instrument).runAsync >>
                    closeObsTable

        val obsStates: Map[Observation.Id, SequenceState] =
          rootModelData.executionState.view.mapValues(_.sequenceState).toMap

        (clientConfigPot, rootModelData.userVault.map(_.toPot).flatten).tupled
          .renderPot: (clientConfig, _) =>
            val loadedObsId: Option[Observation.Id] =
              rootModelData.nighttimeObservation.map(_.obsId)

            val renderExploreLinkToObs
              : Reusable[Either[(Program.Id, Observation.Id), ObservationReference] => VdomNode] =
              Reusable.always: obsIdOrRef =>
                <.a(
                  ^.href := clientConfig.linkToExploreObs(obsIdOrRef).toString,
                  ^.target.blank,
                  ^.onClick ==> (e => e.stopPropagationCB),
                  ObserveStyles.ExternalLink
                )(Icons.ExternalLink).withTooltip(
                  content = "Open in Explore",
                  position = Tooltip.Position.Top,
                  showDelay = 200
                )

            <.div(ObserveStyles.MainPanel)(
              rootModelData.readyObservations
                .map:
                  _.filterNot(_.workflowState === ObservationWorkflowState.Completed)
                    .map: obs =>
                      SessionQueueRow(
                        obs,
                        rootModelData.executionState
                          .get(obs.obsId)
                          .map(_.sequenceState)
                          .getOrElse(SequenceState.Idle),
                        props.rootModel.data.get.observer,
                        ObsClass.Nighttime,
                        loadedObsId.contains_(obs.obsId),
                        // We can't easily know step numbers nor the total number of steps.
                        // Maybe we want to show pending and total times instead?
                        none,
                        none,
                        false
                      )
                .renderPot(
                  ObsListPopup(
                    _,
                    obsStates,
                    loadedObs,
                    loadObservation,
                    isObsTableOpen,
                    renderExploreLinkToObs
                  )
                ),
              rootModelData.nighttimeObsSummary
                .map:
                  ObservationExecutionDisplay(
                    _,
                    props.rootModel.data,
                    openObsTable,
                    renderExploreLinkToObs
                  )
              ,
              Accordion(
                clazz = ObserveStyles.LogArea,
                tabs = List(
                  AccordionTab(header = "Show Log")(
                    <.div(^.height := "200px")(
                      LogArea(clientConfig.site.timezone, rootModelData.globalLog)
                    )
                  )
                )
              )
            )
    )
