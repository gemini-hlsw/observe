// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components

import cats.syntax.all.*
import crystal.Pot
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.enums.Instrument
import lucuma.core.model.Observation
import lucuma.react.common.ReactFnComponent
import lucuma.react.common.ReactFnProps
import lucuma.react.primereact.*
import lucuma.ui.syntax.all.*
import observe.model.ClientConfig
import observe.ui.ObserveStyles
import observe.ui.components.sequence.ObservationExecutionDisplay
import observe.ui.model.AppContext
import observe.ui.model.RootModel
import observe.ui.model.RootModelData
import observe.ui.services.SequenceApi

case class Sequence(rootModel: RootModel, instrument: Instrument) extends ReactFnProps(Home)

object Home
    extends ReactFnComponent[Sequence](props =>
      for
        ctx         <- useContext(AppContext.ctx)
        sequenceApi <- useContext(SequenceApi.ctx)
      yield
        val clientConfigPot: Pot[ClientConfig] = props.rootModel.clientConfig
        val rootModelData: RootModelData       = props.rootModel.data.get

        val loadedObsId: Option[Observation.Id] =
          rootModelData.loadedObsByInstrument.get(props.instrument)

        (clientConfigPot, props.rootModel.renderExploreLinkToObs).tupled
          .renderPot: (clientConfig, renderExploreLinkToObs) =>
            <.div(ObserveStyles.MainPanel)(
              loadedObsId
                .flatMap(rootModelData.readyObservationsMap.get)
                .map:
                  ObservationExecutionDisplay(
                    _,
                    props.rootModel.data,
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
