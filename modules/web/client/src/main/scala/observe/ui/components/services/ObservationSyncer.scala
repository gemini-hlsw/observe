// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.services

import cats.syntax.all.*
import crystal.react.*
import observe.ui.model.LoadedObservation
import lucuma.react.common.ReactFnProps
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.ui.reusability.given
import lucuma.schemas.odb.SequenceSQL
import cats.effect.IO
import observe.ui.model.AppContext
import observe.ui.DefaultErrorPolicy

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

            // TODO We will have to requery under certain conditions:
            // - After step is executed/paused/aborted.
            // - If sequence changes... How do we know this??? Update: Shane will add a hash to the API
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
          .orEmpty
      .render: _ =>
        EmptyVdom
