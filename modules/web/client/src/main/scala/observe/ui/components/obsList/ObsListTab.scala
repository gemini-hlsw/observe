// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.obsList

import cats.syntax.all.given
import crystal.react.*
import japgolly.scalajs.react.*
import lucuma.react.common.*
import lucuma.ui.syntax.all.*
import observe.model.Observation
import observe.ui.components.*
import observe.ui.model.AppContext
import observe.ui.model.RootModel
import observe.ui.model.RootModelData
import observe.ui.model.enums.AppTab

final case class ObsListTab(rootModel: RootModel) extends ReactFnProps(ObsListTab)

object ObsListTab
    extends ReactFnComponent[ObsListTab](props =>
      for
        ctx         <- useContext(AppContext.ctx)
        sequenceApi <- useContext(observe.ui.services.SequenceApi.ctx)
      yield
        import ctx.given

        val rootModelDataView: View[RootModelData] = props.rootModel.data

        val rootModelData: RootModelData = rootModelDataView.get

        val loadObservation: Reusable[Observation.Id => Callback] =
          Reusable
            .implicitly(rootModelData.readyObservationsMap.keySet)
            .withValue: obsId =>
              rootModelData.readyObservationsMap
                .get(obsId)
                .foldMap: obsRow =>
                  rootModelDataView.mod(_.withLoadedObservation(obsId, obsRow.instrument)) >>
                    sequenceApi.loadObservation(obsId, obsRow.instrument).runAsync >>
                    ctx.pushPage(AppTab.LoadedObs(obsRow.instrument))

        (rootModelData.readyObservations, props.rootModel.renderExploreLinkToObs).tupled
          .renderPot: (observations, renderExploreLinkToObs) =>
            ObsList(
              observations,
              rootModelData.executionState,
              rootModelData.observer,
              rootModelData.loadedObservations,
              loadObservation,
              renderExploreLinkToObs
            )
    )
