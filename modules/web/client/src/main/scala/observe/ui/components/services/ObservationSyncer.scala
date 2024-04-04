// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.services

import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all.*
import clue.PersistentClientStatus
import clue.ResponseException
import crystal.react.*
import crystal.react.hooks.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.react.common.ReactFnProps
import lucuma.schemas.odb.SequenceSQL
import lucuma.schemas.odb.input.*
import lucuma.ui.reusability.given
import lucuma.ui.syntax.effect.*
import observe.queries.ObsQueriesGQL
import observe.ui.DefaultErrorPolicy
import observe.ui.model.AppContext
import observe.ui.model.LoadedObservation
import observe.ui.model.reusability.given
import observe.ui.services.ODBQueryApi
import observe.ui.services.SequenceApi

// Renderless component that reloads observation summaries and sequences when observations are selected.
case class ObservationSyncer(nighttimeObservation: View[Option[LoadedObservation]])
    extends ReactFnProps(ObservationSyncer.component)

object ObservationSyncer:
  private type Props = ObservationSyncer

  private val component =
    ScalaFnComponent
      .withHooks[Props]
      .useContext(AppContext.ctx)
      .useContext(SequenceApi.ctx)
      .useContext(ODBQueryApi.ctx)
      .useStreamOnMountBy: (_, ctx, _, _) =>
        ctx.odbClient.statusStream
      .useRef(none[Observation.Id])
      .useAsyncEffectWithDepsBy((props, _, _, _, odbStatusPot, _) =>
        // Run when observation changes or ODB status changes to Initialized
        (props.nighttimeObservation.get.map(_.obsId),
         odbStatusPot.toOption.filter(_ === PersistentClientStatus.Initialized)
        ).tupled
      ): (props, ctx, sequenceApi, odbQueryApi, _, subscribedObsId) =>
        deps =>
          import ctx.given

          deps
            .map: (obsId, _) =>
              if (!subscribedObsId.value.contains(obsId))
                val sequenceUpdate =
                  SequenceSQL
                    .SequenceQuery[IO]
                    .query(obsId)
                    .adaptError:
                      case ResponseException(errors, _) =>
                        Exception(errors.map(_.message).toList.mkString("\n"))
                    .map(_.observation.map(_.execution.config))
                    .attempt
                    .map:
                      _.flatMap:
                        _.toRight:
                          Exception(s"Execution Configuration not defined for observation [$obsId]")
                    .flatMap: config =>
                      props.nighttimeObservation.async.mod(_.map(_.withConfig(config)))

                subscribedObsId.setAsync(obsId.some) >>
                  (sequenceUpdate, odbQueryApi.refreshNighttimeVisits).parTupled
                    .reRunOnResourceSignals:
                      // Eventually, there will be another subscription notifying of sequence/visits changes
                      ObsQueriesGQL.SingleObservationEditSubscription
                        .subscribe[IO]:
                          obsId.toObservationEditInput
                    .flatMap: stream =>
                      Resource.make(stream.compile.drain.start)(_.cancel)
                    .allocated
                    .map(_._2) // Update fiber will get cancelled and subscription ended if connection lost or obs changes
              else IO(IO.unit)

            // TODO Breakpoint initialization should happen in the server, not here.
            // Leaving the code commented here until we move it to the server.
            // >>= ((_, configEither) =>
            // configEither.toOption
            //   .map: config =>
            //     def getBreakPoints(sequence: Option[ExecutionSequence[?]]): Set[Step.Id] =
            //       sequence
            //         .map(s => s.nextAtom +: s.possibleFuture)
            //         .orEmpty
            //         .flatMap(_.steps.toList)
            //         .collect { case s if s.breakpoint === Breakpoint.Enabled => s.id }
            //         .toSet

            //     val initialBreakpoints: Set[Step.Id] =
            //       config match
            //         case InstrumentExecutionConfig.GmosNorth(executionConfig) =>
            //           getBreakPoints(executionConfig.acquisition) ++
            //             getBreakPoints(executionConfig.science)
            //         case InstrumentExecutionConfig.GmosSouth(executionConfig) =>
            //           getBreakPoints(executionConfig.acquisition) ++
            //             getBreakPoints(executionConfig.science)

            //     sequenceApi.setBreakpoints(obsId, initialBreakpoints.toList, Breakpoint.Enabled)
            // .orEmpty
            // )
            .getOrElse:
              // If connection broken, or observation unselected, cleanup sequence and visits
              props.nighttimeObservation.async.mod(_.map(_.reset)).as(IO.unit)
      .render: _ =>
        EmptyVdom
