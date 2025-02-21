// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import crystal.*
import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.core.model.ObservationReference
import lucuma.core.model.Program
import lucuma.react.common.*
import observe.model.SequenceState
import observe.model.SystemOverrides
import observe.ui.ObserveStyles
import observe.ui.model.ObsSummary
import observe.ui.model.ObservationRequests

case class ObsHeader(
  observation:      ObsSummary,
  loadedObsId:      Option[Pot[Observation.Id]], // May be different than shown observation
  loadObs:          Observation.Id => Callback,
  refreshing:       Pot[View[Boolean]],
  sequenceState:    SequenceState,
  requests:         ObservationRequests,
  overrides:        Option[View[SystemOverrides]],
  linkToExploreObs: Either[(Program.Id, Observation.Id), ObservationReference] => VdomNode
) extends ReactFnProps(ObsHeader)

object ObsHeader
    extends ReactFnComponent[ObsHeader](props =>
      <.div(ObserveStyles.ObsSummary)(
        <.div(ObserveStyles.ObsSummaryTitle)(
          SeqControlButtons(
            props.observation.obsId,
            props.loadedObsId,
            props.loadObs,
            props.refreshing,
            props.sequenceState,
            props.requests,
            props.observation.instrument
          ),
          s"${props.observation.title} [${props.observation.obsId}]",
          props.linkToExploreObs:
            props.observation.obsReference
              .toRight((props.observation.programId, props.observation.obsId))
        ),
        <.div(ObserveStyles.ObsSummaryDetails)(
          <.span(props.observation.configurationSummary),
          <.span(props.observation.constraintsSummary),
          props.overrides
            .map: overrides =>
              SubsystemOverrides(props.observation.obsId, props.observation.instrument, overrides)
                .when(props.loadedObsId.contains_(props.observation.obsId.ready))
            .whenDefined
        )
      )
    )
