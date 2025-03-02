// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.components.services

import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all.*
import clue.PersistentClientStatus
import crystal.*
import crystal.react.*
import crystal.react.hooks.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import lucuma.core.model.Observation
import lucuma.react.common.ReactFnComponent
import lucuma.react.common.ReactFnProps
import lucuma.schemas.odb.input.*
import lucuma.ui.reusability.given
import lucuma.ui.syntax.effect.*
import observe.model.SequenceState
import observe.queries.ObsQueriesGQL
import observe.ui.model.AppContext
import observe.ui.model.LoadedObservation
import observe.ui.services.ODBQueryApi
import observe.ui.services.SequenceApi

// Renderless component that reloads observation summaries and sequences when observations are selected.
case class ObservationSyncer(
  nighttimeObservation:              View[Option[LoadedObservation]],
  nighttimeObservationSequenceState: SequenceState
) extends ReactFnProps(ObservationSyncer)

object ObservationSyncer
    extends ReactFnComponent[ObservationSyncer](props =>
      for
        ctx                <- useContext(AppContext.ctx)
        sequenceApi        <- useContext(SequenceApi.ctx)
        odbQueryApi        <- useContext(ODBQueryApi.ctx)
        subscribedObsId    <- useRef(none[Observation.Id])
        stoppedSignal      <- useSignalStream(!props.nighttimeObservationSequenceState.isRunning)
        odbConnectedSignal <-
          useMemo(stoppedSignal):
            _.map: // Reusable
              _.map: // Pot
                _.filter(_ === true) // Only signal when sequence is stopped
                  .void
                  .merge: // Signal when ODB reconnects
                    ctx.odbClient.statusStream.changes
                      .filter(_ === PersistentClientStatus.Connected)
                      .void
        _                  <-
          useEffectStreamResourceWithDeps(
            (props.nighttimeObservation.get.map(_.obsId).toPot,
             odbConnectedSignal.sequencePot
            ).tupled.toOption
          ): deps =>
            import ctx.given

            deps
              .map: (obsId, odbConnectedSignal) =>
                val obsChangedSubscription: Resource[IO, fs2.Stream[IO, Unit]] =
                  ObsQueriesGQL.SingleObservationEditSubscription
                    .subscribe[IO](obsId.toObservationEditInput)
                    .logGraphQLErrors: _ =>
                      "Error received in ObsQueriesGQL.SingleObservationEditSubscription"
                    .map(_.void)

                val datasetChangedSubscription: Resource[IO, fs2.Stream[IO, Unit]] =
                  ObsQueriesGQL.DatasetEditSubscription
                    .subscribe[IO](obsId)
                    .logGraphQLErrors: _ =>
                      "Error received in ObsQueriesGQL.DatasetEditSubscription"
                    .map(_.void)

                val requerySignal: Resource[IO, fs2.Stream[IO, Unit]] =
                  (obsChangedSubscription, datasetChangedSubscription).mapN(_.merge(_))

                Option
                  .unless(subscribedObsId.value.contains(obsId)):
                    Resource.pure(
                      fs2.Stream.eval:
                        subscribedObsId.setAsync(obsId.some)
                    ) >>
                      (odbQueryApi.refreshNighttimeSequence,
                       odbQueryApi.refreshNighttimeVisits
                      ).parTupled.void
                        .reRunOnResourceSignals:
                          requerySignal
                            .map(_.merge(odbConnectedSignal))
                  .orEmpty
              .getOrElse:
                // If connection broken, or observation unselected, cleanup sequence and visits
                Resource.pure:
                  fs2.Stream.eval:
                    props.nighttimeObservation.async.mod(_.map(_.reset))
      yield EmptyVdom
    )
