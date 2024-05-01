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
import lucuma.react.common.ReactFnProps
import lucuma.react.primereact.*
import lucuma.ui.syntax.all.*
import observe.model.SequenceState
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

case class Home(rootModel: RootModel) extends ReactFnProps(Home.component)

object Home:
  private type Props = Home

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useContext(SequenceApi.ctx)
      .render: (props, ctx, sequenceApi) =>
        import ctx.given

        val rootModelData: RootModelData = props.rootModel.data.get

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

        rootModelData.userVault
          .map(_.toPot)
          .flatten
          .renderPot: userVault =>
            <.div(ObserveStyles.MainPanel)(
              Splitter(
                layout = Layout.Vertical,
                stateKey = "main-splitter",
                stateStorage = StateStorage.Local,
                clazz = ObserveStyles.Shrinkable
              )(
                SplitterPanel(clazz = ObserveStyles.TopPanel)(
                  rootModelData.readyObservations
                    .map(
                      _.map(obs =>
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
                      )
                    )
                    .renderPot(
                      SessionQueue(_, obsStates, selectObservation, loadedObs, loadObservation)
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
                    .map(ObservationExecutionDisplay(_, props.rootModel.data, loadObservation))
                )
              ),
              Accordion(tabs =
                List(
                  AccordionTab(clazz = ObserveStyles.LogArea, header = "Show Log")(
                    <.div(^.height := "200px")
                  )
                )
              )
            )
