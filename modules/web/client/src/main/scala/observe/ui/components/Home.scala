// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.*
import crystal.Pot
import crystal.react.*
import crystal.syntax.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
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
import observe.ui.components.queue.SessionQueue
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

        val selectObservation: Observation.Id => Callback =
          obsId =>
            props.rootModel.data
              .zoom(RootModelData.selectedObservation)
              .set(obsId.some)

        val loadObservation: Observation.Id => Callback = obsId =>
          rootModelData.readyObservationsMap
            .get(obsId)
            .foldMap: obsRow =>
              props.rootModel.data
                .zoom(RootModelData.nighttimeObservation)
                .set(LoadedObservation(obsId).some) >>
                sequenceApi.loadObservation(obsId, obsRow.instrument).runAsync

        val obsStates: Map[Observation.Id, SequenceState] =
          rootModelData.executionState.view.mapValues(_.sequenceState).toMap

        (clientConfigPot, rootModelData.userVault.map(_.toPot).flatten).tupled
          .renderPot: (clientConfig, _) =>

            def renderExploreLinkToObs(
              obsIdOrRef: Either[(Program.Id, Observation.Id), ObservationReference]
            ): VdomNode =
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
              Splitter(
                layout = Layout.Vertical,
                stateKey = "main-splitter",
                stateStorage = StateStorage.Local,
                clazz = ObserveStyles.Shrinkable
              )(
                SplitterPanel(clazz = ObserveStyles.TopPanel)(
                  rootModelData.readyObservations
                    .map:
                      _.map: obs =>
                        SessionQueueRow(
                          obs,
                          SequenceState.Idle,
                          props.rootModel.data.get.observer,
                          ObsClass.Nighttime,
                          false, // obs.activeStatus === ObsActiveStatus.Active,
                          none,
                          none,
                          false
                        )
                    .renderPot(
                      SessionQueue(
                        _,
                        obsStates,
                        selectObservation,
                        loadedObs,
                        loadObservation,
                        renderExploreLinkToObs
                      )
                    ),
                  ConfigPanel(
                    rootModelData.nighttimeObservation.map(_.obsId),
                    props.rootModel.data.zoom(RootModelData.observer),
                    props.rootModel.data.zoom(RootModelData.operator),
                    props.rootModel.data.zoom(RootModelData.conditions)
                  )
                ),
                SplitterPanel()(
                  rootModelData.nighttimeDisplayedObservation
                    .map(
                      ObservationExecutionDisplay(
                        _,
                        props.rootModel.data,
                        loadObservation,
                        renderExploreLinkToObs
                      )
                    )
                )
              ),
              Accordion(tabs =
                List(
                  AccordionTab(clazz = ObserveStyles.LogArea, header = "Show Log")(
                    <.div(^.height := "200px")(
                      LogArea(clientConfig.site.timezone, rootModelData.globalLog)
                    )
                  )
                )
              )
            )
    )
