// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.services

import cats.effect.IO
import cats.syntax.all.*
import crystal.react.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.react.common.ReactFnProps
import lucuma.schemas.odb.SequenceSQL
import lucuma.ui.reusability.given
import observe.queries.ObsQueriesGQL
import observe.ui.DefaultErrorPolicy
import observe.ui.model.AppContext
import observe.ui.model.LoadedObservation

// Renderless component that reloads observation summaries and sequences when observations are selected.
case class ObservationSyncer(nighttimeObservation: View[Option[LoadedObservation]])
    extends ReactFnProps(ObservationSyncer.component)

object ObservationSyncer:
  private type Props = ObservationSyncer

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useEffectWithDepsBy((props, _) => props.nighttimeObservation.get.map(_.obsId)):
        (props, ctx) =>
          _.map: obsId =>
            import ctx.given

            val fetchSummary =
              ObsQueriesGQL
                .ObservationSummary[IO]
                .query(obsId)
                .map(_.observation)
                .attempt
                .map:
                  _.flatMap:
                    _.toRight:
                      Exception(s"Observation [$obsId] not found")
                .flatMap: obs =>
                  props.nighttimeObservation.async.mod(_.map(_.withSummary(obs)))

            // TODO We will have to requery under certain conditions:
            // - After step is executed/paused/aborted.
            // - If sequence changes... How do we know this??? Update: Shane will add a hash to the API
            val fetchSequence =
              SequenceSQL
                .SequenceQuery[IO]
                .query(obsId)
                .map(_.observation.map(_.execution.config))
                .attempt
                .map:
                  _.flatMap:
                    _.toRight:
                      Exception(s"Execution Configuration not defined for observation [$obsId]")
                .flatMap: config =>
                  props.nighttimeObservation.async.mod(_.map(_.withConfig(config)))

            (fetchSummary, fetchSequence).tupled.void
            // List(fetchSequence, fetchSummary).sequence.void
            // fetchSummary
            // fetchSequence
          .orEmpty
      .render: _ =>
        EmptyVdom