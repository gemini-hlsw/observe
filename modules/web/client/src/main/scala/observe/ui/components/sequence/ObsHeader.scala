// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.sequence

import cats.syntax.all.*
import crystal.*
import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.react.common.*
import observe.model.SequenceState
import observe.model.SystemOverrides
import observe.ui.ObserveStyles
import observe.ui.model.ObsSummary
import observe.ui.model.ObservationRequests

case class ObsHeader(
  observation:   ObsSummary,
  loadedObsId:   Option[Pot[Observation.Id]], // May be different than shown observation
  loadObs:       Observation.Id => Callback,
  sequenceState: SequenceState,
  requests:      ObservationRequests,
  overrides:     Option[View[SystemOverrides]],
  expandButton:  VdomNode
) extends ReactFnProps(ObsHeader.component)

object ObsHeader:
  private type Props = ObsHeader

  private val component =
    ScalaFnComponent[Props]: props =>
      <.div(ObserveStyles.ObsSummary)(
        <.div(ObserveStyles.ObsSummaryTitle)(
          SeqControlButtons(
            props.observation.obsId,
            props.loadedObsId,
            props.loadObs,
            props.sequenceState,
            props.requests
          ),
          s"${props.observation.title} [${props.observation.obsId}]"
        ),
        <.div(ObserveStyles.ObsSummaryDetails)(
          props.expandButton,
          <.span(props.observation.configurationSummary),
          <.span(props.observation.constraintsSummary),
          props.overrides
            .map: overrides =>
              SubsystemOverrides(props.observation.obsId, props.observation.instrument, overrides)
                .when(props.loadedObsId.contains_(props.observation.obsId.ready))
            .whenDefined
        )
      )
